-- 支持的语言字典
-- 用于 i18n 字段的语言选项
-- 采用 BCP-47 标准语言代码

INSERT INTO sys_dict_type (code, name, i18n_key, status, description)
VALUES (
  'supported_languages',
  '支持的语言',
  'dict.supportedLanguages',
  'active',
  '系统支持的语言列表，用于多语言字段（如固件版本 i18n 升级说明）'
)
ON CONFLICT (code) DO NOTHING;

-- 插入常用语言数据
WITH dict_type AS (
  SELECT id
  FROM sys_dict_type
  WHERE code = 'supported_languages'
  LIMIT 1
)
INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status)
SELECT
  dict_type.id,
  t.label,
  t.value,
  t.i18n_key,
  t.sort_order,
  'active'
FROM dict_type
JOIN (
  VALUES
    -- 简体中文
    (
      '简体中文',
      'zh-CN',
      'language.zhCN',
      10
    ),
    -- 美式英语
    (
      'English (US)',
      'en-US',
      'language.enUS',
      20
    ),
    -- 日语
    (
      '日本語',
      'ja-JP',
      'language.jaJP',
      30
    ),
    -- 韩语
    (
      '한국어',
      'ko-KR',
      'language.koKR',
      40
    ),
    -- 德语
    (
      'Deutsch',
      'de-DE',
      'language.deDE',
      50
    ),
    -- 法语
    (
      'Français',
      'fr-FR',
      'language.frFR',
      60
    ),
    -- 西班牙语
    (
      'Español',
      'es-ES',
      'language.esES',
      70
    ),
    -- 意大利语
    (
      'Italiano',
      'it-IT',
      'language.itIT',
      80
    ),
    -- 葡萄牙语
    (
      'Português',
      'pt-PT',
      'language.ptPT',
      90
    ),
    -- 俄语
    (
      'Русский',
      'ru-RU',
      'language.ruRU',
      100
    ),
    -- 阿拉伯语
    (
      'العربية',
      'ar-SA',
      'language.arSA',
      110
    ),
    -- 繁体中文
    (
      '繁體中文',
      'zh-TW',
      'language.zhTW',
      120
    )
) AS t(label, value, i18n_key, sort_order) ON TRUE
ON CONFLICT (dict_type_id, value) DO NOTHING;
