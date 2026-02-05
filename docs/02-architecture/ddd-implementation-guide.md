# DDD 实施指南

- 文档版本：v1.0
- 创建日期：2026-02-05
- 适用范围：wewins-fota-new 项目从三层架构迁移到 DDD 架构

## 目录

1. [快速开始](#快速开始)
2. [基础类型定义](#基础类型定义)
3. [聚合设计模板](#聚合设计模板)
4. [应用服务模板](#应用服务模板)
5. [仓储实现模板](#仓储实现模板)
6. [常见问题](#常见问题)

---

## 快速开始

### Day 3：创建 DDD 模块骨架

```bash
# 1. 创建新的 Maven 模块
cd fota-domain
mkdir -p fota-domain-product/src/main/java/com/wewins/fota/domain/product
mkdir -p fota-domain-device/src/main/java/com/wewins/fota/domain/device
mkdir -p fota-domain-upgrade/src/main/java/com/wewins/fota/domain/upgrade
mkdir -p fota-domain-shared/src/main/java/com/wewins/fota/domain/shared

cd ../fota-application
mkdir -p fota-application-upgrade/src/main/java/com/wewins/fota/application/upgrade
mkdir -p fota-application-device/src/main/java/com/wewins/fota/application/device

# 2. 添加模块到 pom.xml
```

### 模块依赖关系

```
fota-interfaces-rest
  ↓ 依赖
fota-application-upgrade
  ↓ 依赖
fota-domain-upgrade
  ↓ 依赖
fota-domain-shared
```

---

## 基础类型定义

### 1. AggregateRoot（聚合根基类）

```java
package com.wewins.fota.domain.shared;

import lombok.Getter;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合根基类
 * <p>
 * 所有聚合根必须继承此类，提供：
 * - 领域事件发布能力
 * - ID 相等性判断
 * </p>
 */
@Getter
public abstract class AggregateRoot<ID> extends AbstractAggregateRoot {

    private final ID id;

    protected AggregateRoot(ID id) {
        this.id = id;
    }

    /**
     * 发布领域事件
     */
    protected void publishEvent(Object event) {
        registerEvent(event);
    }
}
```

### 2. Entity（实体基类）

```java
package com.wewins.fota.domain.shared;

import lombok.Getter;
import java.util.Objects;

/**
 * 实体基类
 * <p>
 * 特点：
 * - 有唯一标识
 * - 可变性
 * - 生命周期由聚合根管理
 * </p>
 */
@Getter
public abstract class Entity<ID> {

    private final ID id;

    protected Entity(ID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

### 3. ValueObject（值对象基类）

```java
package com.wewins.fota.domain.shared;

import java.util.Objects;

/**
 * 值对象基类
 * <p>
 * 特点：
 * - 不可变（immutable）
 * - 通过属性值相等
 * - 可替换
 * </p>
 */
public abstract class ValueObject {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true; // 子类通过属性比较
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }
}
```

### 4. DomainEvent（领域事件标记）

```java
package com.wewins.fota.domain.shared;

import java.time.LocalDateTime;

/**
 * 领域事件接口
 */
public interface DomainEvent {

    /**
     * 事件发生时间
     */
    LocalDateTime occurredOn();

    /**
     * 事件类型（用于序列化和路由）
     */
    String eventType();

    /**
     * 聚合根 ID（用于事件溯源）
     */
    String aggregateId();
}
```

### 5. Repository（仓储接口标记）

```java
package com.wewins.fota.domain.shared;

/**
 * 仓储接口标记
 * <p>
 * 所有仓储接口继承此接口
 * </p>
 */
public interface Repository<T, ID> {

    /**
     * 保存聚合根
     */
    T save(T aggregate);

    /**
     * 根据 ID 查找
     */
    java.util.Optional<T> findById(ID id);

    /**
     * 删除聚合根
     */
    void deleteById(ID id);
}
```

---

## 聚合设计模板

### Product 聚合示例

#### 1. 聚合根

```java
package com.wewins.fota.domain.product.model;

import com.wewins.fota.domain.product.event.FirmwarePublished;
import com.wewins.fota.domain.product.value.*;
import com.wewins.fota.domain.shared.AggregateRoot;
import com.wewins.fota.domain.shared.DomainEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 产品聚合根
 * <p>
 * 职责：
 * - 管理产品基本信息
 * - 管理固件版本列表
 * - 保证固件版本唯一性
 * - 发布固件发布事件
 * </p>
 */
@Slf4j
@Getter
public class Product extends AggregateRoot<ProductId> {

    private String name;
    private String manufacturer;
    private String model;
    private String description;
    private final Map<FirmwareVersion, FirmwarePackage> firmwarePackages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 私有构造函数（使用工厂模式创建）
    private Product(ProductId id, String name, String manufacturer, String model) {
        super(id);
        this.name = name;
        this.manufacturer = manufacturer;
        this.model = model;
        this.firmwarePackages = new LinkedHashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 工厂方法：创建新产品
     */
    public static Product create(ProductId id, String name, String manufacturer, String model) {
        Product product = new Product(id, name, manufacturer, model);
        log.info("Product created: id={}, name={}", id, name);
        return product;
    }

    /**
     * 业务方法：发布固件
     * <p>
     * 业务规则：
     * 1. 固件版本不能重复
     * 2. Checksum 必须校验通过
     * 3. 发布 FirmwarePublished 事件
     * </p>
     */
    public void publishFirmware(
        FirmwareVersion version,
        DownloadUri downloadUri,
        Checksum checksum,
        FileMetadata metadata
    ) {
        // 1. 业务规则检查
        if (firmwarePackages.containsKey(version)) {
            throw new FirmwareVersionAlreadyExistsException(version);
        }

        // 2. 创建固件包
        FirmwarePackage firmwarePackage = FirmwarePackage.create(
            version,
            downloadUri,
            checksum,
            metadata
        );

        // 3. 添加到聚合
        firmwarePackages.put(version, firmwarePackage);
        this.updatedAt = LocalDateTime.now();

        // 4. 发布领域事件
        FirmwarePublished event = new FirmwarePublished(
            this.getId().getValue(),
            version.getValue(),
            downloadUri.getValue(),
            checksum.getValue(),
            LocalDateTime.now()
        );
        this.publishEvent(event);

        log.info("Firmware published: productId={}, version={}", this.getId(), version);
    }

    /**
     * 业务方法：获取最新固件版本
     */
    public Optional<FirmwarePackage> getLatestFirmware() {
        return firmwarePackages.values().stream()
            .max(Comparator.comparing(FirmwarePackage::getPublishedAt));
    }

    /**
     * 业务方法：检查固件版本是否存在
     */
    public boolean hasFirmwareVersion(FirmwareVersion version) {
        return firmwarePackages.containsKey(version);
    }

    /**
     * 业务方法：更新产品信息
     */
    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
        log.info("Product updated: id={}, name={}", this.getId(), name);
    }

    /**
     * 获取所有固件版本（按版本号排序）
     */
    public List<FirmwarePackage> getAllFirmwarePackages() {
        return firmwarePackages.values().stream()
            .sorted(Comparator.comparing(FirmwarePackage::getVersion))
            .toList();
    }
}
```

#### 2. 实体

```java
package com.wewins.fota.domain.product.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 固件包实体
 * <p>
 * 内联在 Product 聚合中，不需要独立的 Repository
 * </p>
 */
@Getter
@AllArgsConstructor
public class FirmwarePackage {

    private final FirmwareVersion version;
    private final DownloadUri downloadUri;
    private final Checksum checksum;
    private final FileMetadata metadata;
    private final LocalDateTime publishedAt;

    /**
     * 工厂方法：创建固件包
     */
    public static FirmwarePackage create(
        FirmwareVersion version,
        DownloadUri downloadUri,
        Checksum checksum,
        FileMetadata metadata
    ) {
        return new FirmwarePackage(
            version,
            downloadUri,
            checksum,
            metadata,
            LocalDateTime.now()
        );
    }

    /**
     * 业务方法：验证文件完整性
     */
    public boolean verifyIntegrity(byte[] fileData, Checksum actualChecksum) {
        return this.checksum.equals(actualChecksum);
    }
}
```

#### 3. 值对象

```java
package com.wewins.fota.domain.product.value;

import com.wewins.fota.domain.shared.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 产品 ID（值对象）
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class ProductId extends ValueObject {

    private final Long value;

    public ProductId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        this.value = value;
    }

    public static ProductId of(Long value) {
        return new ProductId(value);
    }
}
```

```java
package com.wewins.fota.domain.product.value;

import com.wewins.fota.domain.shared.ValueObject;
import lombok.Getter;

import java.util.Objects;

/**
 * 固件版本（值对象）
 * <p>
 * 遵循语义化版本：major.minor.patch
 * </p>
 */
@Getter
public class FirmwareVersion extends ValueObject {

    private final String value;

    public FirmwareVersion(String value) {
        if (!isValidVersion(value)) {
            throw new IllegalArgumentException("Invalid firmware version: " + value);
        }
        this.value = value;
    }

    public static FirmwareVersion of(String value) {
        return new FirmwareVersion(value);
    }

    /**
     * 业务方法：版本比较
     */
    public boolean isNewerThan(FirmwareVersion other) {
        String[] thisParts = this.value.split("\\.");
        String[] otherParts = other.value.split("\\.");

        for (int i = 0; i < 3; i++) {
            int thisPart = Integer.parseInt(thisParts[i]);
            int otherPart = Integer.parseInt(otherParts[i]);

            if (thisPart > otherPart) return true;
            if (thisPart < otherPart) return false;
        }

        return false;
    }

    private boolean isValidVersion(String value) {
        return value.matches("\\d+\\.\\d+\\.\\d+");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FirmwareVersion that = (FirmwareVersion) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
```

```java
package com.wewins.fota.domain.product.value;

import com.wewins.fota.domain.shared.ValueObject;
import lombok.Getter;

import java.util.Objects;

/**
 * 校验和（值对象）
 */
@Getter
public class Checksum extends ValueObject {

    private final String value; // MD5 or SHA256
    private final ChecksumAlgorithm algorithm;

    public Checksum(String value, ChecksumAlgorithm algorithm) {
        this.value = value;
        this.algorithm = algorithm;
    }

    public static Checksum md5(String value) {
        return new Checksum(value, ChecksumAlgorithm.MD5);
    }

    public static Checksum sha256(String value) {
        return new Checksum(value, ChecksumAlgorithm.SHA256);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Checksum checksum = (Checksum) o;
        return algorithm == checksum.algorithm && Objects.equals(value, checksum.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, algorithm);
    }

    public enum ChecksumAlgorithm {
        MD5, SHA256
    }
}
```

#### 4. 领域事件

```java
package com.wewins.fota.domain.product.event;

import com.wewins.fota.domain.shared.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 固件发布事件
 */
@Getter
public class FirmwarePublished implements DomainEvent {

    private final String aggregateId;
    private final String version;
    private final String downloadUri;
    private final String checksum;
    private final LocalDateTime occurredOn;

    public FirmwarePublished(
        String productId,
        String version,
        String downloadUri,
        String checksum,
        LocalDateTime occurredOn
    ) {
        this.aggregateId = productId;
        this.version = version;
        this.downloadUri = downloadUri;
        this.checksum = checksum;
        this.occurredOn = occurredOn;
    }

    @Override
    public String eventType() {
        return "FirmwarePublished";
    }
}
```

#### 5. 仓储接口

```java
package com.wewins.fota.domain.product.repository;

import com.wewins.fota.domain.product.model.Product;
import com.wewins.fota.domain.product.value.ProductId;
import com.wewins.fota.domain.shared.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 产品仓储接口
 * <p>
 * 定义在领域层，实现在基础设施层
 * </p>
 */
public interface ProductRepository extends Repository<Product, ProductId> {

    /**
     * 根据 ID 查找产品
     */
    Optional<Product> findById(ProductId id);

    /**
     * 查找所有产品
     */
    List<Product> findAll();

    /**
     * 根据名称查找产品
     */
    Optional<Product> findByName(String name);

    /**
     * 删除产品
     */
    void deleteById(ProductId id);
}
```

#### 6. 领域服务

```java
package com.wewins.fota.domain.product.service;

import com.wewins.fota.domain.product.model.Product;
import com.wewins.fota.domain.product.value.FirmwareVersion;
import com.wewins.fota.domain.product.value.ProductId;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 产品领域服务
 * <p>
 * 不属于某个聚合的业务逻辑
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDomainService {

    private final ProductRepository productRepository;

    /**
     * 业务方法：检查产品是否存在
     */
    public boolean exists(ProductId productId) {
        return productRepository.findById(productId).isPresent();
    }

    /**
     * 业务方法：获取产品及其固件版本
     */
    public Product getProductWithFirmware(ProductId productId, FirmwareVersion version) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!product.hasFirmwareVersion(version)) {
            throw new FirmwareVersionNotFoundException(productId, version);
        }

        return product;
    }
}
```

---

## 应用服务模板

```java
package com.wewins.fota.application.product;

import com.wewins.fota.application.product.command.PublishFirmwareCommand;
import com.wewins.fota.application.product.dto.FirmwareDto;
import com.wewins.fota.domain.product.model.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.product.value.*;
import com.wewins.fota.domain.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产品管理应用服务
 * <p>
 * 职责：
 * - 用例编排
 * - 事务边界
 * - 调用领域层
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductManagementAppService {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * 用例：发布固件
     */
    @Transactional
    public FirmwareDto publishFirmware(PublishFirmwareCommand command) {
        log.info("Publishing firmware: productId={}, version={}",
            command.getProductId(), command.getVersion());

        // 1. 加载聚合
        Product product = productRepository.findById(ProductId.of(command.getProductId()))
            .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));

        // 2. 调用领域方法
        product.publishFirmware(
            FirmwareVersion.of(command.getVersion()),
            DownloadUri.of(command.getDownloadUri()),
            Checksum.sha256(command.getChecksum()),
            FileMetadata.of(command.getFileSize(), command.getFileType())
        );

        // 3. 保存聚合
        productRepository.save(product);

        // 4. 发布领域事件
        eventPublisher.publishEvents(product);

        log.info("Firmware published successfully: productId={}, version={}",
            command.getProductId(), command.getVersion());

        // 5. 返回 DTO
        return FirmwareDto.from(
            product.getId().getValue(),
            command.getVersion(),
            command.getDownloadUri()
        );
    }

    /**
     * 用例：查询产品详情
     */
    @Transactional(readOnly = true)
    public ProductDto getProductDetail(Long productId) {
        Product product = productRepository.findById(ProductId.of(productId))
            .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductDto.from(product);
    }
}
```

---

## 仓储实现模板

```java
package com.wewins.fota.infrastructure.persistence.mybatis;

import com.wewins.fota.domain.product.model.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.product.value.ProductId;
import com.wewins.fota.infrastructure.persistence.mybatis.po.ProductPO;
import com.wewins.fota.infrastructure.persistence.mybatis.converter.ProductPoConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 产品仓储实现（MyBatis-Plus）
 * <p>
 * 职责：
 * - Domain ↔ PO 转换
 * - 调用 MyBatis-Plus Mapper
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;
    private final ProductPoConverter converter;

    @Override
    public Product save(Product product) {
        log.debug("Saving product: id={}", product.getId());

        ProductPO po = converter.toPo(product);
        productMapper.insert(po);

        return converter.toDomain(po);
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        ProductPO po = productMapper.selectById(id.getValue());
        return Optional.ofNullable(po)
            .map(converter::toDomain);
    }

    @Override
    public List<Product> findAll() {
        List<ProductPO> pos = productMapper.selectList(null);
        return pos.stream()
            .map(converter::toDomain)
            .toList();
    }

    @Override
    public void deleteById(ProductId id) {
        productMapper.deleteById(id.getValue());
    }
}
```

**PO 转换器**：

```java
package com.wewins.fota.infrastructure.persistence.mybatis.converter;

import com.wewins.fota.domain.product.model.Product;
import com.wewins.fota.domain.product.value.*;
import com.wewins.fota.infrastructure.persistence.mybatis.po.ProductPO;
import org.springframework.stereotype.Component;

@Component
public class ProductPoConverter {

    public Product toDomain(ProductPO po) {
        return Product.create(
            ProductId.of(po.getId()),
            po.getName(),
            po.getManufacturer(),
            po.getModel()
        );
    }

    public ProductPO toPo(Product product) {
        ProductPO po = new ProductPO();
        po.setId(product.getId().getValue());
        po.setName(product.getName());
        po.setManufacturer(product.getManufacturer());
        po.setModel(product.getModel());
        return po;
    }
}
```

---

## 常见问题

### Q1: 聚合根应该有多大？

**A**: 聚合根应该尽可能小，只包含保证一致性边界所需的实体和值对象。

- ✅ 好的设计：Product + FirmwarePackage（紧密关联）
- ❌ 坏的设计：Product + Device（跨越多个限界上下文）

### Q2: 值对象何时使用？

**A**: 当满足以下条件时，使用值对象：

1. 没有唯一标识
2. 不可变（immutable）
3. 通过属性值相等
4. 可替换

示例：FirmwareVersion, Checksum, DownloadUri, Money, Email

### Q3: 领域服务 vs 应用服务？

**A**:

| 领域服务 | 应用服务 |
|---------|---------|
| 属于领域层 | 属于应用层 |
| 封装业务规则 | 用例编排 |
| 无状态 | 有事务边界 |
| 示例：UpgradeDecisionService | 示例：UpgradeCheckAppService |

### Q4: 如何处理跨聚合查询？

**A**: 使用 CQRS（命令查询职责分离）：

- **写操作**：通过聚合根的 Repository
- **读操作**：通过专用的查询服务（直接读数据库，返回 DTO）

```java
// 写：更新设备版本
device.upgradeVersion(newVersion);
deviceRepository.save(device);

// 读：查询设备列表（直接查询，不走聚合）
List<DeviceDto> devices = deviceQueryService.findByProductId(productId);
```

### Q5: 领域事件如何发布？

**A**: 使用 Spring 的事件机制：

```java
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishEvents(AggregateRoot<?> aggregateRoot) {
        aggregateRoot.domainEvents()
            .forEach(eventPublisher::publishEvent);
        aggregateRoot.clearDomainEvents();
    }
}
```

---

## 下一步

1. **Day 3**：创建 DDD 模块骨架
2. **Day 4**：定义基础类型（AggregateRoot, Entity, ValueObject）
3. **Day 5**：提取 Product 聚合
4. **Day 6-7**：提取 Device、UpgradePolicy 聚合

详见：[DDD 架构设计文档](./ddd-architecture.md)
