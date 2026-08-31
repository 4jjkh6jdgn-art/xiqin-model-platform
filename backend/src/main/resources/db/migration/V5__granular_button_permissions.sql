-- 页面与按钮级权限。保留仍可直接使用的上传、下载、删除等旧编码，
-- 并把旧的模块级权限自动展开，避免已有自定义角色升级后丢失能力。
INSERT INTO permissions (name, code, module, description) VALUES
    ('查看仪表盘', 'dashboard:view', 'dashboard', '进入仪表盘并查看统计信息'),

    ('查看模型库', 'model:view', 'model_library', '进入模型库、搜索并查看模型详情'),
    ('上传模型', 'model:upload', 'model_library', '显示上传模型按钮并允许上传或更新版本'),
    ('编辑模型资料', 'model:edit', 'model_library', '编辑模型名称、分类、项目和描述'),
    ('删除模型', 'model:delete', 'model_library', '删除模型及其关联资料'),
    ('下载模型资料', 'model:download', 'model_detail', '下载单个文件或版本 ZIP'),
    ('编辑模型文件', 'model:file_edit', 'model_detail', '修改模型文件和贴图名称'),
    ('删除模型文件', 'model:file_delete', 'model_detail', '删除模型版本中的文件'),
    ('管理预览场景', 'model:scene_manage', 'model_detail', '保存初始视角和灯光参数'),
    ('管理缩略图', 'model:thumbnail_manage', 'model_detail', '截取、切换和删除缩略图'),
    ('反馈模型问题', 'model:feedback', 'model_detail', '提交模型问题反馈'),
    ('查看模型记录', 'model:history_view', 'model_detail', '查看版本历史和修改记录'),
    ('查看分类管理', 'model:category_view', 'model_data', '进入模型与项目分类管理'),
    ('新增模型分类', 'model:category_create', 'model_data', '新增模型分类'),
    ('编辑模型分类', 'model:category_edit', 'model_data', '编辑模型分类'),
    ('删除模型分类', 'model:category_delete', 'model_data', '删除模型分类'),
    ('查看上传记录', 'model:upload_records_view', 'model_data', '查看平台模型上传记录'),
    ('查看下载记录', 'model:download_records_view', 'model_data', '查看平台模型下载记录'),

    ('查看项目列表', 'project:view', 'project_list', '进入项目列表和项目详情'),
    ('新建项目', 'project:create', 'project_list', '新建项目'),
    ('编辑项目信息', 'project:edit', 'project_detail', '修改项目资料并生成新版本'),
    ('删除项目', 'project:delete', 'project_list', '删除项目'),
    ('查看项目版本', 'project:version_view', 'project_detail', '查看和切换项目历史版本'),
    ('查看项目成员', 'project:member_view', 'project_detail', '查看项目成员和角色'),
    ('管理项目成员', 'project:member_manage', 'project_detail', '搜索、添加和移除项目成员'),
    ('查看任务排班', 'project:task_view', 'project_detail', '查看任务列表、看板和排班日历'),
    ('新增任务', 'project:task_create', 'project_detail', '新增项目任务'),
    ('更新任务', 'project:task_edit', 'project_detail', '更新任务状态和内容'),
    ('删除任务', 'project:task_delete', 'project_detail', '删除项目任务'),
    ('查看项目阶段', 'project:stage_view', 'project_detail', '查看项目阶段'),
    ('管理项目阶段', 'project:stage_manage', 'project_detail', '新增和调整项目阶段'),
    ('查看项目资料', 'project:file_view', 'project_detail', '浏览、预览项目文件和文件夹'),
    ('上传项目资料', 'project:file_upload', 'project_detail', '上传文件或整个文件夹'),
    ('编辑项目资料', 'project:file_edit', 'project_detail', '在线编辑文件内容和移动文件'),
    ('下载项目资料', 'project:file_download', 'project_detail', '下载项目文件'),
    ('管理项目文件夹', 'project:folder_manage', 'project_detail', '新建文件夹并拖放整理资料'),
    ('查看文件记录', 'project:file_activity_view', 'project_detail', '查看文件上传、下载和更新记录'),
    ('反馈项目问题', 'project:feedback', 'project_detail', '提交项目或项目文件问题反馈'),
    ('查看项目分类', 'project:category_view', 'model_data', '查看项目分类'),
    ('新增项目分类', 'project:category_create', 'model_data', '新增项目分类'),
    ('编辑项目分类', 'project:category_edit', 'model_data', '编辑项目分类'),
    ('删除项目分类', 'project:category_delete', 'model_data', '删除项目分类'),

    ('查看用户', 'user:view', 'organization_user', '进入用户管理并搜索用户'),
    ('编辑用户', 'user:edit', 'organization_user', '编辑用户资料和所属角色'),
    ('启用或停用用户', 'user:status', 'organization_user', '切换用户账号状态'),
    ('删除用户', 'user:delete', 'organization_user', '删除用户'),
    ('查看角色', 'role:view', 'organization_role', '进入角色管理并查看权限'),
    ('新建角色', 'role:create', 'organization_role', '新建自定义角色'),
    ('编辑角色', 'role:edit', 'organization_role', '编辑自定义角色'),
    ('删除角色', 'role:delete', 'organization_role', '删除自定义角色'),
    ('分配角色权限', 'role:permission_assign', 'organization_role', '为自定义角色分配页面与按钮权限'),
    ('查看注册申请', 'registration:view', 'organization_registration', '查看待审批注册申请'),
    ('审批注册申请', 'registration:approve', 'organization_registration', '通过或拒绝注册申请'),
    ('生成邀请码', 'invitation:create', 'organization_registration', '生成并复制注册邀请码'),

    ('查看站内信箱', 'notification:view', 'notification', '查看上传错误、问题反馈和提醒'),
    ('处理站内消息', 'notification:read', 'notification', '标记消息已读或全部已读'),
    ('发布工作提醒', 'notification:publish', 'notification', '向站内信箱发布工作提醒'),
    ('编辑个人资料', 'profile:edit', 'profile', '修改自己的个人资料'),
    ('系统设置', 'system:config', 'system', '管理系统级配置')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

-- 将已有模块级权限展开到按钮级权限。
WITH permission_map(legacy_code, detailed_code) AS (
    VALUES
        ('user:manage', 'user:view'), ('user:manage', 'user:edit'),
        ('user:manage', 'user:status'), ('user:manage', 'user:delete'),
        ('role:manage', 'role:view'), ('role:manage', 'role:create'),
        ('role:manage', 'role:edit'), ('role:manage', 'role:delete'),
        ('role:manage', 'role:permission_assign'),
        ('model:manage', 'model:view'), ('model:manage', 'model:edit'),
        ('model:manage', 'model:file_edit'), ('model:manage', 'model:file_delete'),
        ('model:manage', 'model:scene_manage'), ('model:manage', 'model:thumbnail_manage'),
        ('model:manage', 'model:feedback'), ('model:manage', 'model:history_view'),
        ('model:manage', 'model:category_view'), ('model:manage', 'model:category_create'),
        ('model:manage', 'model:category_edit'), ('model:manage', 'model:category_delete'),
        ('model:manage', 'model:upload_records_view'), ('model:manage', 'model:download_records_view'),
        ('project:manage', 'project:view'), ('project:manage', 'project:create'),
        ('project:manage', 'project:edit'), ('project:manage', 'project:delete'),
        ('project:manage', 'project:version_view'), ('project:manage', 'project:member_view'),
        ('project:manage', 'project:member_manage'), ('project:manage', 'project:task_view'),
        ('project:manage', 'project:task_create'), ('project:manage', 'project:task_edit'),
        ('project:manage', 'project:task_delete'), ('project:manage', 'project:stage_view'),
        ('project:manage', 'project:stage_manage'), ('project:manage', 'project:file_view'),
        ('project:manage', 'project:file_upload'), ('project:manage', 'project:file_edit'),
        ('project:manage', 'project:file_download'), ('project:manage', 'project:folder_manage'),
        ('project:manage', 'project:file_activity_view'), ('project:manage', 'project:feedback'),
        ('project:manage', 'project:category_view'), ('project:manage', 'project:category_create'),
        ('project:manage', 'project:category_edit'), ('project:manage', 'project:category_delete'),
        ('invitation:manage', 'invitation:create')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, detailed.id
FROM role_permissions rp
JOIN permissions legacy ON legacy.id = rp.permission_id
JOIN permission_map pm ON pm.legacy_code = legacy.code
JOIN permissions detailed ON detailed.code = pm.detailed_code
ON CONFLICT DO NOTHING;

-- 所有已登录角色都需要基础页面入口；系统角色获得符合当前产品行为的默认按钮权限。
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('dashboard:view', 'profile:edit')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'model:view', 'model:upload', 'model:download', 'model:feedback',
    'project:view', 'project:version_view', 'project:member_view', 'project:task_view',
    'project:task_create', 'project:task_edit', 'project:file_view', 'project:file_upload',
    'project:file_edit', 'project:file_download', 'project:folder_manage',
    'project:file_activity_view', 'project:feedback'
) WHERE r.code = 'member'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'model:view', 'model:upload', 'model:download', 'model:edit', 'model:file_edit',
    'model:file_delete', 'model:scene_manage', 'model:thumbnail_manage', 'model:feedback',
    'model:history_view', 'model:category_view', 'model:category_create', 'model:category_edit',
    'model:upload_records_view', 'model:download_records_view',
    'project:view', 'project:create', 'project:edit', 'project:delete', 'project:version_view',
    'project:member_view', 'project:member_manage', 'project:task_view', 'project:task_create',
    'project:task_edit', 'project:task_delete', 'project:stage_view', 'project:stage_manage',
    'project:file_view', 'project:file_upload', 'project:file_edit', 'project:file_download',
    'project:folder_manage', 'project:file_activity_view', 'project:feedback',
    'project:category_view', 'project:category_create', 'project:category_edit', 'project:category_delete',
    'registration:view', 'registration:approve', 'invitation:create'
) WHERE r.code = 'leader'
ON CONFLICT DO NOTHING;

-- 管理员始终拥有当前及未来迁移中声明的全部权限。
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;

-- 删除已展开的纯模块级权限，角色界面只展示可实际对应页面或按钮的权限。
DELETE FROM permissions
WHERE code IN ('user:manage', 'role:manage', 'model:manage', 'project:manage', 'invitation:manage');
