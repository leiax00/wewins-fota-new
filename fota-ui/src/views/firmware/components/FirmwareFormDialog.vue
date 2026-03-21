<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import JsonFieldEditor from '@/components/json-field/JsonFieldEditor.vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { UploadState, FirmwareFormData } from '../types'
import type { JsonFieldDefinition } from '@/components/json-field/types/json-field'
import FirmwareUploadPanel from './FirmwareUploadPanel.vue'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  editingId: number | null
  form: FirmwareFormData
  formRules: FormRules
  uploadState: UploadState
  productSearchOptions: { id: number; name: string }[]
  productSearchLoading: boolean
  tagsSchema: JsonFieldDefinition[]
  metaSchema: JsonFieldDefinition[]
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submit'): void
  (e: 'cancel-upload'): void
  (e: 'retry-upload'): void
  (e: 'product-search', keyword: string): void
  (e: 'upload-file', file: File): void
  (e: 'update:form', value: FirmwareFormData): void
}>()

const formRef = ref<FormInstance>()

const dialogTitle = computed(() => {
  return props.mode === 'create' ? t('firmware.add') : t('common.edit')
})

const isEdit = computed(() => props.mode === 'edit')

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    emit('submit')
  } catch {
    // 验证失败
  }
}

const handleProductSearch = (keyword: string) => {
  emit('product-search', keyword)
}

const handleCancelUpload = () => {
  emit('cancel-upload')
}

const handleRetryUpload = () => {
  emit('retry-upload')
}

const handleUploadFile = (file: File) => {
  emit('upload-file', file)
}

// 监听 noPackage 变化
watch(
  () => props.form.noPackage,
  (val) => {
    if (val) {
      handleCancelUpload()
    }
    formRef.value?.validateField('uploadSessionId')
  }
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="680px"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="formRef?.resetFields()"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item
        prop="productId"
        :label="t('firmware.product')"
      >
        <el-select
          v-model="form.productId"
          :loading="productSearchLoading"
          :disabled="isEdit"
          filterable
          remote
          reserve-keyword
          :remote-method="handleProductSearch"
          style="width: 100%"
        >
          <el-option
            v-for="product in productSearchOptions"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        prop="version"
        :label="t('firmware.version')"
      >
        <el-input
          v-model="form.version"
          placeholder="1.0.0"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item
        prop="internalVersion"
        :label="t('firmware.internalVersion')"
      >
        <el-input
          v-model="form.internalVersion"
          :placeholder="t('firmware.internalVersionPlaceholder')"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item :label="t('firmware.noPackageVersion')">
        <el-switch v-model="form.noPackage" />
        <span class="ml-2 text-sm text-gray-500">
          {{ `（${t('firmware.noPackageVersionTip')}）` }}
        </span>
      </el-form-item>

      <el-form-item
        v-if="!form.noPackage"
        prop="uploadSessionId"
        :label="t('firmware.uploadPackage')"
      >
        <FirmwareUploadPanel
          :upload-state="uploadState"
          :product-id="form.productId"
          :disabled="submitting"
          :submitting="submitting"
          @upload="handleUploadFile"
          @cancel="handleCancelUpload"
          @retry="handleRetryUpload"
        />
      </el-form-item>

      <el-form-item :label="t('firmware.tags')">
        <JsonFieldEditor
          v-model="form.tags"
          dict-type-code="json_schema.firmware_tags"
          mode="form"
          class="w-full"
          :allow-mode-switch="true"
          :disabled="submitting"
        />
      </el-form-item>

      <el-form-item :label="t('firmware.meta')">
        <JsonFieldEditor
          v-model="form.meta"
          dict-type-code="json_schema.firmware_meta"
          mode="form"
          class="w-full"
          :allow-mode-switch="true"
          :disabled="submitting"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="uploadState.status === 'UPLOADING'"
        @click="handleSubmit"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>
