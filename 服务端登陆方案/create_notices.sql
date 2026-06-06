CREATE TABLE `notices` (
  `notice_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知UUID',
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知内容',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'info' COMMENT '类型: info/warning/error',
  `priority` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal' COMMENT '优先级: normal/high',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态: active/inactive',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

-- 插入测试数据
INSERT INTO `notices` (`notice_id`, `title`, `content`, `type`, `priority`, `status`, `created_at`, `updated_at`) VALUES
('550e8400-e29b-41d4-a716-446655440020', '系统维护通知', '为了提供更好的服务，我们将在本周六凌晨 2:00 进行服务器升级维护，预计时长 30 分钟。期间可能出现短暂的服务不可用。', 'warning', 'high', 'active', NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440021', '新功能上线：Sora 视频生成', '激动人心！我们上线了全新的视频生成模型 Sora 2.0，支持更长的时长和更清晰的画质。快去体验吧！', 'info', 'normal', 'active', NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440022', 'API 服务异常公告', '由于上游供应商抖动，图片生成服务可能出现间歇性延迟。技术团队正在紧急排查中，给您带来不便敬请谅解。', 'error', 'high', 'active', NOW(), NOW());
