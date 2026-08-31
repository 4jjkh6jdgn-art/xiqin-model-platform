<template>
  <div v-loading="loading" class="project-workbench">
    <header class="project-heading">
      <el-button text class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon><span>返回项目列表</span>
      </el-button>
      <div class="project-identity">
        <div class="identity-line">
          <h1>{{ displayProject?.name || '项目详情' }}</h1>
          <el-tag size="small" effect="light" :type="projectStatusType(displayProject?.status)">{{ statusText(displayProject?.status) }}</el-tag>
        </div>
        <div v-if="canViewVersions" class="version-switcher">
          <span>项目版本</span>
          <el-select :model-value="selectedVersion" size="small" class="version-select" popper-class="project-version-popper" @change="selectProjectVersion">
            <el-option v-for="version in versions" :key="version.versionNum" :label="`v${version.versionNum}${version.versionNum === project?.defaultVersion ? ' · 默认' : ''}`" :value="version.versionNum">
              <div class="version-option">
                <strong>v{{ version.versionNum }}</strong>
                <span class="version-change-log">{{ version.changeLog || '项目版本' }}</span>
                <span v-if="version.versionNum === project?.defaultVersion" class="version-default-state">当前默认</span>
                <el-button v-else v-permission="'project:default_version_manage'" link type="primary" size="small" @mousedown.stop.prevent @click.stop.prevent="setProjectDefaultVersion(version.versionNum)">默认显示</el-button>
              </div>
            </el-option>
          </el-select>
          <span v-if="selectedVersion !== project?.currentVersion" class="history-hint">正在查看历史快照</span>
        </div>
      </div>
      <div class="heading-actions">
        <el-button v-permission="'project:feedback'" @click="openFeedback('project', projectId, project?.name)"><el-icon><ChatDotRound /></el-icon>问题反馈</el-button>
      </div>
    </header>

    <div class="project-layout">
      <aside class="project-sidebar">
        <section class="panel info-panel">
          <div class="panel-header">
            <div><h2>项目信息</h2><p>当前项目基础资料</p></div>
            <el-button v-if="selectedVersion === project?.currentVersion" v-permission="'project:edit'" text circle title="编辑项目信息" @click="openEditProject"><el-icon><EditPen /></el-icon></el-button>
          </div>
          <div class="project-description">{{ displayProject?.description || '暂未填写项目描述' }}</div>
          <div class="info-grid">
            <div><span>状态</span><strong>{{ statusText(displayProject?.status) }}</strong></div>
            <div><span>优先级</span><strong>P{{ displayProject?.priority || 1 }}</strong></div>
            <div><span>当前版本</span><strong>v{{ selectedVersion || 1 }}</strong></div>
            <div><span>创建时间</span><strong>{{ formatDate(project?.createdAt) }}</strong></div>
          </div>
          <div v-if="selectedVersion !== project?.currentVersion" class="snapshot-note"><el-icon><Clock /></el-icon><span>{{ selectedVersionData?.changeLog || '历史版本' }} · {{ formatTime(selectedVersionData?.createdAt) }}</span></div>
        </section>

        <section v-if="canViewMembers" class="panel members-panel">
          <div class="panel-header">
            <div><h2>项目成员 <span class="count-pill">{{ members.length }}</span></h2><p>成员与项目角色</p></div>
            <el-button v-permission="'project:member_manage'" size="small" @click="openMemberDialog"><el-icon><Plus /></el-icon>添加成员</el-button>
          </div>
          <div class="member-list">
            <div v-for="member in members" :key="member.userId" class="member-row">
              <el-avatar :size="36" :src="member.avatarUrl">{{ member.username?.slice(0, 1)?.toUpperCase() }}</el-avatar>
              <div class="member-copy"><strong>{{ member.username }}</strong><span>{{ member.email || member.systemRoleName || '系统成员' }}</span></div>
              <el-tag size="small" effect="plain">{{ projectRoleText(member.projectRole) }}</el-tag>
              <el-button v-if="member.userId !== project?.createdBy" v-permission="'project:member_manage'" text type="danger" circle title="移除成员" @click="removeMember(member.userId)"><el-icon><Close /></el-icon></el-button>
            </div>
            <el-empty v-if="!members.length" description="暂无项目成员" :image-size="62" />
          </div>
        </section>
      </aside>

      <section class="workspace-panel">
        <el-tabs v-model="activeTab" class="workspace-tabs">
          <el-tab-pane v-if="canViewTasks" name="tasks">
            <template #label><span class="tab-label">任务排班 <em>{{ tasks.length }}</em></span></template>
            <div class="tab-toolbar">
              <div class="view-switch">
                <button :class="{ active: taskView === 'list' }" @click="taskView = 'list'"><el-icon><List /></el-icon>列表</button>
                <button :class="{ active: taskView === 'board' }" @click="taskView = 'board'"><el-icon><Grid /></el-icon>看板</button>
                <button :class="{ active: taskView === 'calendar' }" @click="taskView = 'calendar'"><el-icon><Calendar /></el-icon>排班日历</button>
              </div>
              <el-button v-permission="'project:task_create'" type="primary" @click="showNewTask = true"><el-icon><Plus /></el-icon>新增任务</el-button>
            </div>

            <el-table v-if="taskView === 'list'" :data="tasks" class="task-table" empty-text="暂无排班任务">
              <el-table-column prop="title" label="任务" min-width="210"><template #default="{ row }"><div class="task-title"><strong>{{ row.title }}</strong><span>{{ row.description || '暂无说明' }}</span></div></template></el-table-column>
              <el-table-column label="负责人" width="150"><template #default="{ row }">{{ memberName(row.assigneeId) }}</template></el-table-column>
              <el-table-column label="截止日期" width="140"><template #default="{ row }">{{ formatDate(row.deadline) }}</template></el-table-column>
              <el-table-column label="优先级" width="90"><template #default="{ row }"><span class="priority-dot" :class="`p${row.priority || 1}`">P{{ row.priority || 1 }}</span></template></el-table-column>
              <el-table-column label="状态" width="130"><template #default="{ row }"><el-select v-permission="'project:task_edit'" :model-value="row.status" size="small" @change="updateTaskStatus(row.id, $event)"><el-option label="待处理" value="pending" /><el-option label="进行中" value="in_progress" /><el-option label="已完成" value="completed" /></el-select><el-tag v-if="!canEditTasks" size="small" :type="taskStatusType(row.status)">{{ phaseStatusText(row.status) }}</el-tag></template></el-table-column>
            </el-table>

            <div v-else-if="taskView === 'board'" class="task-board">
              <div v-for="column in taskColumns" :key="column.value" class="board-column">
                <div class="board-header"><span><i :class="column.value"></i>{{ column.label }}</span><em>{{ tasksByStatus(column.value).length }}</em></div>
                <div class="board-list">
                  <article v-for="task in tasksByStatus(column.value)" :key="task.id" class="task-card">
                    <strong>{{ task.title }}</strong><p>{{ task.description || '暂无任务说明' }}</p><div><span>{{ memberName(task.assigneeId) }}</span><time>{{ formatDate(task.deadline) }}</time></div>
                    <el-select v-permission="'project:task_edit'" :model-value="task.status" size="small" @change="updateTaskStatus(task.id, $event)"><el-option v-for="target in taskColumns" :key="target.value" :label="`移至${target.label}`" :value="target.value" /></el-select>
                  </article>
                  <div v-if="!tasksByStatus(column.value).length" class="board-empty">暂无任务</div>
                </div>
              </div>
            </div>

            <el-calendar v-else v-model="calendarDate" class="schedule-calendar">
              <template #date-cell="{ data }"><div class="calendar-cell"><span class="day-number">{{ Number(data.day.split('-')[2]) }}</span><button v-for="task in calendarTasks(data.day)" :key="task.id" class="calendar-task" :class="task.status" @click.stop="canEditTasks && updateTask(task.id, { status: task.status === 'completed' ? 'pending' : 'completed' })">{{ task.title }}</button></div></template>
            </el-calendar>
          </el-tab-pane>

          <el-tab-pane v-if="canViewFiles" name="files">
            <template #label><span class="tab-label">项目资料 <em>{{ files.length }}</em></span></template>
            <div class="file-toolbar">
              <div class="breadcrumb-wrap" @dragover.prevent @drop="dropFile(null)">
                <button @click="openFolder(null)"><el-icon><House /></el-icon>项目根目录</button>
                <template v-for="crumb in folderBreadcrumbs" :key="crumb.id"><el-icon><ArrowRight /></el-icon><button @click="openFolder(crumb.id)">{{ crumb.name }}</button></template>
              </div>
              <div class="file-actions">
                <el-button v-permission="'project:folder_manage'" @click="showCreateFolder = true"><el-icon><FolderAdd /></el-icon>新增文件夹</el-button>
                <el-button v-permission="'project:file_upload'" @click="fileInput?.click()"><el-icon><Upload /></el-icon>上传文件</el-button>
                <el-button v-permission="'project:file_upload'" type="primary" @click="folderInput?.click()"><el-icon><FolderOpened /></el-icon>上传整个文件夹</el-button>
              </div>
            </div>
            <input ref="fileInput" class="hidden-input" type="file" multiple @change="handleFileSelection" />
            <input ref="folderInput" class="hidden-input" type="file" multiple webkitdirectory directory @change="handleFolderSelection" />
            <div v-if="currentFolders.length" class="folder-grid">
              <button v-for="folder in currentFolders" :key="folder.id" class="folder-card" @click="openFolder(folder.id)" @dragover.prevent @drop.stop="dropFile(folder.id)"><span class="folder-icon"><el-icon><Folder /></el-icon></span><span><strong>{{ folder.name }}</strong><small>{{ folderItemCount(folder.id) }} 项内容</small></span><el-icon class="folder-chevron"><ArrowRight /></el-icon></button>
            </div>
            <div class="file-list" :class="{ empty: !currentFiles.length, 'grid-view': fileView === 'grid' }">
              <div class="file-list-head">
                <div class="file-head-labels"><span>文件名称</span><span>版本</span><span>上传时间</span><span>操作</span></div>
                <div class="file-view-switch" title="排列方式">
                  <button :class="{ active: fileView === 'list' }" title="列表排列" @click="fileView = 'list'"><el-icon><List /></el-icon></button>
                  <button :class="{ active: fileView === 'grid' }" title="网格排列" @click="fileView = 'grid'"><el-icon><Grid /></el-icon></button>
                </div>
              </div>
              <template v-if="fileView === 'list'">
                <div v-for="file in currentFiles" :key="file.id" class="file-row" :draggable="canEditFiles" @dragstart="startFileDrag(file.id)">
                  <div class="file-name-cell"><span class="file-type-icon" :class="fileExtension(file.fileName)">{{ fileExtension(file.fileName).toUpperCase().slice(0, 4) }}</span><span><strong>{{ file.fileName }}</strong><small>{{ formatSize(file.fileSize) }} · {{ uploaderName(file.uploadedBy) }}</small></span></div>
                  <span class="file-version">v{{ file.version || 1 }}</span><span class="file-time">{{ formatDate(file.createdAt) }}</span>
                  <div class="row-actions"><el-button text type="primary" @click="openFileDetails(file)">详情</el-button><el-button text @click="openPreview(file)">查看</el-button><el-button v-permission="'project:file_edit'" text @click="openEditor(file)">编辑</el-button><el-button v-permission="'project:file_download'" text @click="downloadFile(file)">下载</el-button></div>
                </div>
                <el-empty v-if="!currentFiles.length" description="当前文件夹暂无资料，可直接拖入或上传" :image-size="78" />
              </template>
              <template v-else>
                <div class="file-grid">
                  <article v-for="file in currentFiles" :key="file.id" class="file-card" :draggable="canEditFiles" @dragstart="startFileDrag(file.id)">
                    <div class="file-card-top">
                      <span class="file-type-icon" :class="fileExtension(file.fileName)">{{ fileExtension(file.fileName).toUpperCase().slice(0, 4) }}</span>
                      <div class="file-card-info"><strong>{{ file.fileName }}</strong><small>{{ formatSize(file.fileSize) }} · {{ uploaderName(file.uploadedBy) }}</small></div>
                    </div>
                    <div class="file-card-meta"><span class="file-version">v{{ file.version || 1 }}</span><span class="file-time">{{ formatDate(file.createdAt) }}</span></div>
                    <div class="file-card-actions"><el-button text type="primary" @click="openFileDetails(file)">详情</el-button><el-button text @click="openPreview(file)">查看</el-button><el-button v-permission="'project:file_edit'" text @click="openEditor(file)">编辑</el-button><el-button v-permission="'project:file_download'" text @click="downloadFile(file)">下载</el-button></div>
                  </article>
                  <el-empty v-if="!currentFiles.length" description="当前文件夹暂无资料，可直接拖入或上传" :image-size="78" />
                </div>
              </template>
            </div>
          </el-tab-pane>

          <el-tab-pane v-if="canViewModels" name="models">
            <template #label><span class="tab-label">项目模型 <em>{{ projectModels.length }}</em></span></template>
            <div class="project-model-toolbar"><div><strong>项目关联模型</strong><span>搜索模型库并关联到当前项目，模型原有项目关系不会受影响</span></div><el-button v-permission="'model:edit'" type="primary" plain @click="openModelLinkDialog"><el-icon><Plus /></el-icon>增加模型</el-button></div>
            <div class="project-model-grid">
              <article v-for="item in projectModels" :key="item.id" class="project-model-card" @click="router.push(`/models/${item.id}`)">
                <div class="project-model-cover"><img v-if="item.thumbnailUrl" :src="item.thumbnailUrl" :alt="item.name"/><div v-else><el-icon><Box /></el-icon></div><el-tag size="small" :type="item.status==='ready'?'success':'warning'">{{ item.status==='ready'?'可用':'处理中' }}</el-tag><el-button v-permission="'model:edit'" class="unlink-model" circle size="small" title="解除当前项目关联" @click.stop="unlinkProjectModel(item)"><el-icon><Close /></el-icon></el-button></div>
                <div class="project-model-copy"><strong>{{ item.name }}</strong><div><span v-for="name in item.fileFormats || []" :key="name">{{ name }}</span></div><small>v{{ item.version || 1 }} · {{ formatSize(item.fileSize) }}</small></div>
              </article>
              <el-empty v-if="!projectModels.length" description="当前项目还没有关联模型，可在上传模型时选择此项目" :image-size="88"/>
            </div>
          </el-tab-pane>

          <el-tab-pane v-if="canViewPhases" name="phases">
            <template #label><span class="tab-label">项目阶段 <em>{{ phases.length }}</em></span></template>
            <div class="phase-list"><article v-for="(phase, index) in phases" :key="phase.id" class="phase-card"><div class="phase-index">{{ String(index + 1).padStart(2, '0') }}</div><div class="phase-copy"><strong>{{ phase.name }}</strong><p>{{ phase.description || '暂无阶段说明' }}</p><span>{{ formatDate(phase.startDate) }} — {{ formatDate(phase.endDate) }}</span></div><el-tag effect="plain" :type="taskStatusType(phase.status)">{{ phaseStatusText(phase.status) }}</el-tag></article><el-empty v-if="!phases.length" description="暂无项目阶段" /></div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <el-drawer v-model="fileDrawerVisible" direction="rtl" size="450px" class="file-detail-drawer" :show-close="false">
      <template #header><div class="drawer-heading"><div><span>文件详情</span><strong>{{ selectedFile?.fileName }}</strong></div><el-button text circle @click="fileDrawerVisible = false"><el-icon><Close /></el-icon></el-button></div></template>
      <div v-if="canViewActivities" class="drawer-search"><el-input v-model="activityKeyword" clearable placeholder="搜索操作人、动作或时间记录" @keyup.enter="loadActivities"><template #prefix><el-icon><Search /></el-icon></template></el-input></div>
      <div class="file-summary-card"><div><span>文件大小</span><strong>{{ formatSize(selectedFile?.fileSize) }}</strong></div><div><span>文件版本</span><strong>v{{ selectedFile?.version || 1 }}</strong></div><div><span>上传人</span><strong>{{ uploaderName(selectedFile?.uploadedBy) }}</strong></div><div><span>更新时间</span><strong>{{ formatTime(selectedFile?.updatedAt) }}</strong></div></div>
      <template v-if="canViewActivities"><div class="drawer-section-title"><span>操作记录</span><el-button text type="primary" @click="loadActivities"><el-icon><Refresh /></el-icon>刷新</el-button></div><el-timeline v-loading="activityLoading" class="activity-timeline"><el-timeline-item v-for="activity in activities" :key="activity.id" :timestamp="formatTime(activity.createdAt)" :type="activityType(activity.action)" placement="top"><div class="activity-card"><strong>{{ activityText(activity.action) }}</strong><p>{{ activity.userName || '系统' }} · {{ activity.detail || '文件操作' }}</p></div></el-timeline-item></el-timeline><el-empty v-if="!activityLoading && !activities.length" description="没有匹配的操作记录" :image-size="65" /></template>
      <div class="drawer-footer"><el-button v-permission="'project:feedback'" @click="openFeedback('project_file', selectedFile?.id, selectedFile?.fileName)"><el-icon><ChatDotRound /></el-icon>反馈该文件问题</el-button><el-button v-permission="'project:file_download'" type="primary" @click="downloadFile(selectedFile)"><el-icon><Download /></el-icon>下载文件</el-button></div>
    </el-drawer>

    <el-dialog v-model="showEditProject" title="编辑项目信息" width="600px" destroy-on-close @closed="resetEditCoverState">
      <el-form label-position="top" class="dialog-form">
        <el-form-item label="项目名称"><el-input v-model="editProjectForm.name" maxlength="80" show-word-limit /></el-form-item>
        <el-form-item label="项目描述"><el-input v-model="editProjectForm.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
        <div class="form-columns">
          <el-form-item label="项目状态"><el-select v-model="editProjectForm.status"><el-option label="规划中" value="planning" /><el-option label="进行中" value="in_progress" /><el-option label="已完成" value="completed" /><el-option label="已归档" value="archived" /></el-select></el-form-item>
          <el-form-item label="优先级"><el-select v-model="editProjectForm.priority"><el-option v-for="n in 4" :key="n" :label="`P${n}`" :value="n" /></el-select></el-form-item>
        </div>
        <el-form-item label="项目封面">
          <div class="edit-cover-field">
            <div class="edit-cover-preview">
              <img v-if="editCoverPreviewUrl" :src="editCoverPreviewUrl" alt="项目封面预览" />
              <div v-else-if="editCoverLoading" class="edit-cover-placeholder"><el-icon class="is-loading"><Loading /></el-icon><span>正在读取封面</span></div>
              <div v-else class="edit-cover-placeholder"><el-icon><Picture /></el-icon><span>{{ editCoverRemoveRequested ? '保存后移除封面' : '暂未设置封面' }}</span></div>
            </div>
            <div class="edit-cover-controls">
              <div v-permission="'project:cover_manage'" class="edit-cover-buttons">
                <el-button type="primary" plain @click="editCoverInputRef?.click()"><el-icon><Upload /></el-icon>{{ project?.coverUrl || editCoverFile ? '更换封面' : '选择封面' }}</el-button>
                <el-button v-if="project?.coverUrl || editCoverFile" type="danger" text @click="clearEditCover">移除封面</el-button>
              </div>
              <p>支持 JPG、PNG、WebP、GIF，最大 8 MB。封面将同步用于项目列表悬停预览。</p>
              <span v-if="editCoverFile" :title="editCoverFile.name">已选择：{{ editCoverFile.name }}</span>
            </div>
            <input ref="editCoverInputRef" class="hidden-input" type="file" accept="image/jpeg,image/png,image/webp,image/gif" @change="handleEditCoverSelected" />
          </div>
        </el-form-item>
        <el-form-item label="版本变更说明"><el-input v-model="editProjectForm.changeLog" placeholder="例如：补充项目描述并调整项目状态" maxlength="120" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showEditProject = false">取消</el-button><el-button v-permission="'project:edit'" type="primary" :loading="savingProject" @click="saveProject">保存并生成新版本</el-button></template>
    </el-dialog>

    <el-dialog v-model="showAddMember" title="添加项目成员" width="650px" destroy-on-close>
      <div class="member-dialog-tools"><el-input v-model="candidateKeyword" clearable placeholder="搜索用户名或邮箱" @input="searchCandidates"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-select v-model="newMemberRole" placeholder="项目角色"><el-option label="项目负责人" value="leader" /><el-option label="协作成员" value="member" /><el-option label="访客" value="viewer" /></el-select></div>
      <div v-loading="candidateLoading" class="candidate-list"><button v-for="candidate in candidates" :key="candidate.id" type="button" :class="{ selected: isCandidateSelected(candidate.id) }" @click="toggleCandidate(candidate.id)"><el-avatar :size="40" :src="candidate.avatarUrl">{{ candidate.username?.slice(0, 1)?.toUpperCase() }}</el-avatar><span><strong>{{ candidate.username }}</strong><small>{{ candidate.email || '未设置邮箱' }}</small></span><el-tag size="small" effect="plain">{{ candidate.roleName || '系统成员' }}</el-tag><el-icon v-if="isCandidateSelected(candidate.id)"><CircleCheckFilled /></el-icon></button><el-empty v-if="!candidateLoading && !candidates.length" description="没有可添加的成员" :image-size="65" /></div>
      <template #footer><span class="candidate-selection-count">已选 {{ selectedCandidateIds.length }} 人</span><el-button @click="showAddMember = false">取消</el-button><el-button v-permission="'project:member_manage'" type="primary" :disabled="!selectedCandidateIds.length" @click="addMember">添加并发送提醒</el-button></template>
    </el-dialog>

    <el-dialog v-model="showAddModels" title="增加项目模型" width="760px" destroy-on-close>
      <div class="model-link-tools">
        <el-input v-model="modelCandidateKeyword" clearable placeholder="搜索模型名称或文件名" @keyup.enter="loadModelCandidates" @clear="loadModelCandidates"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button type="primary" :loading="modelCandidateLoading" @click="loadModelCandidates"><el-icon><Search /></el-icon>查找模型</el-button>
      </div>
      <div v-loading="modelCandidateLoading" class="model-candidate-grid">
        <button v-for="item in modelCandidates" :key="item.id" type="button" :class="{ selected: selectedModelIds.includes(item.id) }" @click="toggleModelCandidate(item.id)">
          <span class="candidate-cover"><img v-if="item.thumbnailUrl" :src="item.thumbnailUrl" alt=""/><el-icon v-else><Box /></el-icon></span>
          <span class="candidate-copy"><strong>{{ item.name }}</strong><small>{{ item.categoryName || '未分类' }} · v{{ item.version || 1 }} · {{ formatSize(item.fileSize) }}</small></span>
          <el-icon v-if="selectedModelIds.includes(item.id)" class="candidate-check"><CircleCheckFilled /></el-icon>
        </button>
        <el-empty v-if="!modelCandidateLoading && !modelCandidates.length" description="没有可关联的模型" :image-size="68" />
      </div>
      <template #footer><span class="candidate-selection-count">已选 {{ selectedModelIds.length }} 个模型</span><el-button @click="showAddModels = false">取消</el-button><el-button v-permission="'model:edit'" type="primary" :loading="linkingModels" :disabled="!selectedModelIds.length" @click="linkSelectedModels">确认关联</el-button></template>
    </el-dialog>

    <el-dialog v-model="showCreateFolder" title="新增文件夹" width="430px"><el-form label-position="top"><el-form-item label="文件夹名称"><el-input v-model="newFolderName" autofocus placeholder="请输入文件夹名称" @keyup.enter="createFolder" /></el-form-item><div class="folder-location">创建位置：{{ folderBreadcrumbText }}</div></el-form><template #footer><el-button @click="showCreateFolder = false">取消</el-button><el-button v-permission="'project:folder_manage'" type="primary" @click="createFolder">创建</el-button></template></el-dialog>

    <el-dialog v-model="showNewTask" title="新增排班任务" width="560px"><el-form label-position="top" class="dialog-form"><el-form-item label="任务名称"><el-input v-model="taskForm.title" maxlength="100" /></el-form-item><el-form-item label="任务说明"><el-input v-model="taskForm.description" type="textarea" :rows="3" /></el-form-item><div class="form-columns"><el-form-item label="负责人"><el-select v-model="taskForm.assigneeId" clearable><el-option v-for="member in members" :key="member.userId" :label="member.username" :value="member.userId" /></el-select></el-form-item><el-form-item label="截止日期"><el-date-picker v-model="taskForm.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></div><el-form-item label="优先级"><el-radio-group v-model="taskForm.priority"><el-radio-button v-for="n in 4" :key="n" :label="n">P{{ n }}</el-radio-button></el-radio-group></el-form-item></el-form><template #footer><el-button @click="showNewTask = false">取消</el-button><el-button v-permission="'project:task_create'" type="primary" @click="createTask">创建任务</el-button></template></el-dialog>

    <el-dialog v-model="feedbackVisible" title="问题反馈" width="520px"><el-alert title="反馈将发送到管理员站内信箱" type="info" :closable="false" show-icon /><el-form label-position="top" class="feedback-form"><el-form-item label="反馈对象"><el-input :model-value="feedbackTargetName" disabled /></el-form-item><el-form-item label="问题说明"><el-input v-model="feedbackContent" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请描述遇到的问题、期望结果及复现方式" /></el-form-item></el-form><template #footer><el-button @click="feedbackVisible = false">取消</el-button><el-button v-permission="'project:feedback'" type="primary" :disabled="!feedbackContent.trim()" @click="submitFeedback">提交反馈</el-button></template></el-dialog>

    <MediaPreviewDialog
      v-model="mediaPreviewVisible"
      :source="mediaPreviewUrl"
      :file-name="mediaPreviewFile?.fileName"
      :media-type="mediaPreviewType"
      :loading="mediaPreviewLoading"
      :error="mediaPreviewError"
      @download="downloadFile(mediaPreviewFile)"
    />
    <el-dialog v-model="showPreview" :title="`文件预览：${previewFile?.fileName || ''}`" width="82%" top="5vh" destroy-on-close @closed="closeGenericPreview"><div v-loading="previewLoading" class="preview-body"><iframe v-if="previewUrl && isPdfFile(previewFile?.fileName)" :src="previewUrl"></iframe><pre v-else-if="previewText" class="preview-text">{{ previewText }}</pre><div v-else class="preview-unsupported"><el-empty :description="previewMessage" :image-size="80" /><el-button type="primary" @click="downloadFile(previewFile)"><el-icon><Download /></el-icon>下载文件</el-button></div></div><template #footer><el-button @click="showPreview = false">关闭</el-button><el-button type="primary" @click="downloadFile(previewFile)"><el-icon><Download /></el-icon>下载</el-button></template></el-dialog>
    <el-dialog v-model="showEditor" :title="editorTitle" width="92%" top="3vh" destroy-on-close @closed="closeEditor"><div v-loading="editorLoading" class="editor-body"><el-alert v-if="editorError" :title="editorError" type="error" :closable="false" /><el-input v-if="editorMode === 'text'" v-model="editorContent" type="textarea" class="text-editor" /><div v-else id="office-editor-container" class="office-editor"></div></div><template v-if="editorMode === 'text'" #footer><el-button @click="showEditor = false">关闭</el-button><el-button v-permission="'project:file_edit'" type="primary" :loading="editorSaving" @click="saveContent">保存修改</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api/project'
import { modelApi } from '@/api/model'
import { notificationApi } from '@/api/index-modules'
import { useAuthStore } from '@/stores/auth'
import MediaPreviewDialog from '@/components/media/MediaPreviewDialog.vue'
import { getMediaPreviewType, type MediaPreviewType } from '@/utils/mediaPreview'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const projectId = Number(route.params.id)
const canViewVersions = computed(() => authStore.hasPermission('project:version_view'))
const canViewMembers = computed(() => authStore.hasPermission('project:member_view'))
const canViewTasks = computed(() => authStore.hasPermission('project:task_view'))
const canEditTasks = computed(() => authStore.hasPermission('project:task_edit'))
const canViewFiles = computed(() => authStore.hasPermission('project:file_view'))
const canEditFiles = computed(() => authStore.hasPermission('project:file_edit'))
const canViewActivities = computed(() => authStore.hasPermission('project:file_activity_view'))
const canViewPhases = computed(() => authStore.hasPermission('project:stage_view'))
const canViewModels = computed(() => authStore.hasPermission('model:view'))
const loading = ref(false)
const project = ref<any>(null)
const versions = ref<any[]>([])
const selectedVersion = ref<number>(1)
const members = ref<any[]>([])
const tasks = ref<any[]>([])
const files = ref<any[]>([])
const folders = ref<any[]>([])
const phases = ref<any[]>([])
const projectModels = ref<any[]>([])
const activeTab = ref('files')
const selectedVersionData = computed(() => versions.value.find(item => item.versionNum === selectedVersion.value))
const displayProject = computed(() => selectedVersion.value === project.value?.currentVersion ? project.value : selectedVersionData.value || project.value)

const loadAll = async () => {
  loading.value = true
  try {
    const p = await projectApi.getProject(projectId)
    project.value = p.data
    const requests: Promise<void>[] = []
    if (canViewVersions.value) requests.push(projectApi.getVersions(projectId).then(res => { versions.value = res.data || [] }))
    if (canViewMembers.value) requests.push(projectApi.getMembers(projectId).then(res => { members.value = res.data || [] }))
    if (canViewTasks.value) requests.push(projectApi.getTasks(projectId).then(res => { tasks.value = res.data || [] }))
    if (canViewFiles.value) {
      requests.push(projectApi.getFiles(projectId).then(res => { files.value = res.data || [] }))
      requests.push(projectApi.getFolders(projectId).then(res => { folders.value = res.data || [] }))
    }
    if (canViewPhases.value) requests.push(projectApi.getPhases(projectId).then(res => { phases.value = res.data || [] }))
    if (canViewModels.value) requests.push(modelApi.getModels({ projectId, page: 0, size: 200 }).then(res => { projectModels.value = res.data?.list || [] }))
    await Promise.all(requests)
    if (canViewVersions.value) {
      const requestedVersion = Number(route.query.version)
      const preferredVersion = requestedVersion || p.data.defaultVersion || p.data.currentVersion || 1
      selectedVersion.value = versions.value.some(item => item.versionNum === preferredVersion)
        ? preferredVersion
        : (p.data.defaultVersion || p.data.currentVersion || 1)
    } else selectedVersion.value = p.data.currentVersion || 1
    const requestedTab = typeof route.query.tab === 'string' ? route.query.tab : ''
    const allowedTabs = [canViewFiles.value && 'files', canViewModels.value && 'models', canViewTasks.value && 'tasks', canViewPhases.value && 'phases'].filter(Boolean)
    activeTab.value = allowedTabs.includes(requestedTab) ? requestedTab : (allowedTabs[0] as string || 'files')
    const requestedFileId = Number(route.query.fileId)
    const requestedFile = requestedFileId ? files.value.find(item => Number(item.id) === requestedFileId) : null
    if (requestedFile && activeTab.value === 'files') {
      currentFolderId.value = requestedFile.folderId ?? null
      await openFileDetails(requestedFile)
    }
  } finally { loading.value = false }
}

const goBack = () => {
  const target = typeof route.query.returnTo === 'string' && route.query.returnTo.startsWith('/') ? route.query.returnTo : ''
  router.push(target || '/projects')
}

const selectProjectVersion = async (value: number) => {
  const version = Number(value)
  if (!version || !versions.value.some(item => item.versionNum === version)) return
  selectedVersion.value = version
  await router.replace({ query: { ...route.query, version: String(version) } })
}

const setProjectDefaultVersion = async (version: number) => {
  if (version === project.value?.defaultVersion) return
  await projectApi.setDefaultVersion(projectId, version)
  project.value.defaultVersion = version
  ElMessage.success(`v${version} 已设为项目默认显示版本`)
}

const showEditProject = ref(false)
const savingProject = ref(false)
const editProjectForm = reactive({ name: '', description: '', status: 'planning', priority: 1, changeLog: '' })
const editCoverInputRef = ref<HTMLInputElement | null>(null)
const editCoverFile = ref<File | null>(null)
const editCoverPreviewUrl = ref('')
const editCoverLoading = ref(false)
const editCoverRemoveRequested = ref(false)

const releaseEditCoverPreview = () => {
  if (editCoverPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(editCoverPreviewUrl.value)
  editCoverPreviewUrl.value = ''
}

const resetEditCoverState = () => {
  releaseEditCoverPreview()
  editCoverFile.value = null
  editCoverLoading.value = false
  editCoverRemoveRequested.value = false
  if (editCoverInputRef.value) editCoverInputRef.value.value = ''
}

const validateProjectCover = (file: File) => {
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('封面仅支持 JPG、PNG、WebP 或 GIF 图片')
    return false
  }
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.warning('封面图片不能超过 8 MB')
    return false
  }
  return true
}

const loadEditProjectCover = async () => {
  if (!project.value?.coverUrl) return
  editCoverLoading.value = true
  try {
    const response = await projectApi.getProjectCover(projectId)
    editCoverPreviewUrl.value = URL.createObjectURL(response.data)
  } catch {
    ElMessage.warning('当前项目封面暂时无法读取，可重新选择封面')
  } finally {
    editCoverLoading.value = false
  }
}

const openEditProject = async () => {
  Object.assign(editProjectForm, {
    name: project.value?.name || '',
    description: project.value?.description || '',
    status: project.value?.status || 'planning',
    priority: project.value?.priority || 1,
    changeLog: ''
  })
  resetEditCoverState()
  showEditProject.value = true
  await loadEditProjectCover()
}

const handleEditCoverSelected = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !validateProjectCover(file)) {
    input.value = ''
    return
  }
  releaseEditCoverPreview()
  editCoverFile.value = file
  editCoverPreviewUrl.value = URL.createObjectURL(file)
  editCoverRemoveRequested.value = false
}

const clearEditCover = () => {
  releaseEditCoverPreview()
  editCoverFile.value = null
  editCoverRemoveRequested.value = Boolean(project.value?.coverUrl)
  if (editCoverInputRef.value) editCoverInputRef.value.value = ''
}

const saveProject = async () => {
  if (!editProjectForm.name.trim()) return ElMessage.warning('请输入项目名称')
  savingProject.value = true
  let coverSaved = true
  try {
    await projectApi.updateProject(projectId, {
      name: editProjectForm.name.trim(),
      description: editProjectForm.description,
      status: editProjectForm.status,
      priority: editProjectForm.priority
    }, editProjectForm.changeLog)
    try {
      if (editCoverFile.value) await projectApi.updateProjectCover(projectId, editCoverFile.value)
      else if (editCoverRemoveRequested.value && project.value?.coverUrl) await projectApi.removeProjectCover(projectId)
    } catch {
      coverSaved = false
    }
    showEditProject.value = false
    await loadAll()
    selectedVersion.value = project.value.currentVersion
    if (coverSaved) ElMessage.success('项目信息与封面已更新，并生成新版本')
    else ElMessage.warning('项目信息已更新，但封面保存失败，请重新进入编辑窗口设置')
  } finally {
    savingProject.value = false
  }
}

const showAddMember = ref(false)
const candidateKeyword = ref('')
const candidateLoading = ref(false)
const candidates = ref<any[]>([])
const selectedCandidateIds = ref<number[]>([])
const newMemberRole = ref('member')
let candidateTimer: number | undefined
const loadCandidates = async () => { candidateLoading.value = true; try { candidates.value = (await projectApi.getMemberCandidates(projectId, candidateKeyword.value || undefined)).data || [] } finally { candidateLoading.value = false } }
const searchCandidates = () => { if (candidateTimer) window.clearTimeout(candidateTimer); candidateTimer = window.setTimeout(loadCandidates, 250) }
const isCandidateSelected = (userId: number) => selectedCandidateIds.value.includes(userId)
const toggleCandidate = (userId: number) => {
  selectedCandidateIds.value = isCandidateSelected(userId)
    ? selectedCandidateIds.value.filter(id => id !== userId)
    : [...selectedCandidateIds.value, userId]
}
const openMemberDialog = () => { candidateKeyword.value = ''; selectedCandidateIds.value = []; newMemberRole.value = 'member'; showAddMember.value = true; loadCandidates() }
const addMember = async () => {
  if (!selectedCandidateIds.value.length) return
  const count = selectedCandidateIds.value.length
  await projectApi.addMember(projectId, { userIds: selectedCandidateIds.value, role: newMemberRole.value })
  ElMessage.success(`已添加 ${count} 位成员，并发送项目提醒`)
  showAddMember.value = false
  await loadAll()
}
const removeMember = async (userId: number) => { await ElMessageBox.confirm('确认将该成员移出项目？', '移除成员', { type: 'warning' }); await projectApi.removeMember(projectId, userId); ElMessage.success('成员已移除'); await loadAll() }

const showAddModels = ref(false)
const modelCandidateKeyword = ref('')
const modelCandidateLoading = ref(false)
const modelCandidates = ref<any[]>([])
const selectedModelIds = ref<number[]>([])
const linkingModels = ref(false)
const loadModelCandidates = async () => {
  modelCandidateLoading.value = true
  try {
    const res = await modelApi.getModels({ keyword: modelCandidateKeyword.value.trim() || undefined, page: 0, size: 100, sortField: 'time', sortDirection: 'desc' })
    const linkedIds = new Set(projectModels.value.map(item => Number(item.id)))
    modelCandidates.value = (res.data?.list || []).filter((item: any) => !linkedIds.has(Number(item.id)))
  } finally { modelCandidateLoading.value = false }
}
const openModelLinkDialog = () => { modelCandidateKeyword.value = ''; selectedModelIds.value = []; showAddModels.value = true; loadModelCandidates() }
const toggleModelCandidate = (modelId: number) => { selectedModelIds.value = selectedModelIds.value.includes(modelId) ? selectedModelIds.value.filter(id => id !== modelId) : [...selectedModelIds.value, modelId] }
const linkSelectedModels = async () => {
  if (!selectedModelIds.value.length) return
  linkingModels.value = true
  try {
    await Promise.all(selectedModelIds.value.map(modelId => modelApi.linkProject(modelId, projectId)))
    ElMessage.success(`已关联 ${selectedModelIds.value.length} 个模型`)
    showAddModels.value = false
    await loadAll()
  } finally { linkingModels.value = false }
}
const unlinkProjectModel = async (item: any) => {
  await ElMessageBox.confirm(`确认解除模型“${item.name}”与当前项目的关联？模型文件不会被删除。`, '解除关联', { type: 'warning' })
  await modelApi.unlinkProject(item.id, projectId)
  projectModels.value = projectModels.value.filter(model => model.id !== item.id)
  ElMessage.success('已解除项目关联')
}

const taskView = ref<'list' | 'board' | 'calendar'>('list')
const calendarDate = ref(new Date())
const showNewTask = ref(false)
const taskForm = reactive<any>({ title: '', description: '', assigneeId: undefined, deadline: '', priority: 1 })
const taskColumns = [{ label: '待处理', value: 'pending' }, { label: '进行中', value: 'in_progress' }, { label: '已完成', value: 'completed' }]
const tasksByStatus = (status: string) => tasks.value.filter(item => item.status === status)
const calendarTasks = (day: string) => tasks.value.filter(item => item.deadline?.slice(0, 10) === day)
const createTask = async () => { if (!taskForm.title.trim()) return ElMessage.warning('请输入任务名称'); await projectApi.createTask(projectId, { ...taskForm }); Object.assign(taskForm, { title: '', description: '', assigneeId: undefined, deadline: '', priority: 1 }); showNewTask.value = false; ElMessage.success('任务已加入排班'); await loadAll() }
const updateTask = async (id: number, data: any) => { await projectApi.updateTask(id, data); const row = tasks.value.find(item => item.id === id); if (row) Object.assign(row, data); ElMessage.success('任务状态已更新') }
const updateTaskStatus = (id: number, value: any) => updateTask(id, { status: value })

const currentFolderId = ref<number | null>(null)
const fileInput = ref<HTMLInputElement>()
const folderInput = ref<HTMLInputElement>()
const draggedFileId = ref<number>()
const fileView = ref<'list' | 'grid'>('list')
const currentFolders = computed(() => folders.value.filter(item => (item.parentId ?? null) === currentFolderId.value))
const currentFiles = computed(() => files.value.filter(item => (item.folderId ?? null) === currentFolderId.value))
const folderBreadcrumbs = computed(() => { const result: any[] = []; let id = currentFolderId.value; while (id) { const folder = folders.value.find(item => item.id === id); if (!folder) break; result.unshift(folder); id = folder.parentId }; return result })
const folderBreadcrumbText = computed(() => ['项目根目录', ...folderBreadcrumbs.value.map(item => item.name)].join(' / '))
const openFolder = (id: number | null) => { currentFolderId.value = id }
const folderItemCount = (id: number) => folders.value.filter(item => item.parentId === id).length + files.value.filter(item => item.folderId === id).length
const startFileDrag = (id: number) => { if (canEditFiles.value) draggedFileId.value = id }
const dropFile = async (folderId: number | null) => { if (!canEditFiles.value || !draggedFileId.value) return; await projectApi.moveFile(draggedFileId.value, folderId); const row = files.value.find(item => item.id === draggedFileId.value); if (row) row.folderId = folderId; draggedFileId.value = undefined; ElMessage.success('文件已移动到指定文件夹') }
const showCreateFolder = ref(false)
const newFolderName = ref('')
const createFolder = async () => { if (!newFolderName.value.trim()) return ElMessage.warning('请输入文件夹名称'); const res = await projectApi.createFolder(projectId, { parentId: currentFolderId.value, name: newFolderName.value.trim() }); folders.value.push(res.data); newFolderName.value = ''; showCreateFolder.value = false; ElMessage.success('文件夹已创建') }
const handleFileSelection = async (event: Event) => { const input = event.target as HTMLInputElement; const selected = Array.from(input.files || []); if (!selected.length) return; loading.value = true; try { for (const file of selected) { const data = new FormData(); data.append('file', file); if (currentFolderId.value) data.append('folderId', String(currentFolderId.value)); await projectApi.uploadFile(projectId, data) }; ElMessage.success(`已上传 ${selected.length} 个文件`); await loadAll() } finally { loading.value = false; input.value = '' } }
const handleFolderSelection = async (event: Event) => { const input = event.target as HTMLInputElement; const selected = Array.from(input.files || []); if (!selected.length) return; const data = new FormData(); selected.forEach(file => { data.append('files', file); data.append('relativePaths', (file as any).webkitRelativePath || file.name) }); if (currentFolderId.value) data.append('targetFolderId', String(currentFolderId.value)); loading.value = true; try { await projectApi.uploadFolder(projectId, data); ElMessage.success(`文件夹上传完成，共 ${selected.length} 个文件`); await loadAll() } finally { loading.value = false; input.value = '' } }

const fileDrawerVisible = ref(false)
const selectedFile = ref<any>(null)
const activities = ref<any[]>([])
const activityKeyword = ref('')
const activityLoading = ref(false)
const openFileDetails = async (file: any) => { selectedFile.value = file; activityKeyword.value = ''; fileDrawerVisible.value = true; await loadActivities() }
const loadActivities = async () => { if (!selectedFile.value || !canViewActivities.value) return; activityLoading.value = true; try { activities.value = (await projectApi.getFileActivities(selectedFile.value.id, activityKeyword.value || undefined)).data || [] } finally { activityLoading.value = false } }

const feedbackVisible = ref(false)
const feedbackContent = ref('')
const feedbackSourceType = ref('project')
const feedbackSourceId = ref<number>()
const feedbackTargetName = ref('')
const openFeedback = (sourceType: string, sourceId?: number, name?: string) => { feedbackSourceType.value = sourceType; feedbackSourceId.value = sourceId; feedbackTargetName.value = name || project.value?.name || '当前项目'; feedbackContent.value = ''; feedbackVisible.value = true }
const submitFeedback = async () => { await notificationApi.createFeedback({ sourceType: feedbackSourceType.value, sourceId: feedbackSourceId.value, projectId, title: `${feedbackTargetName.value}的问题反馈`, content: feedbackContent.value.trim() }); feedbackVisible.value = false; ElMessage.success('反馈已提交至管理员站内信箱') }

const showPreview = ref(false)
const previewFile = ref<any>(null)
const previewUrl = ref<string | null>(null)
const previewLoading = ref(false)
const previewMessage = ref('暂不支持浏览器内直接预览')
const previewText = ref('')
const mediaPreviewVisible = ref(false)
const mediaPreviewFile = ref<any>(null)
const mediaPreviewUrl = ref('')
const mediaPreviewType = ref<MediaPreviewType>('image')
const mediaPreviewLoading = ref(false)
const mediaPreviewError = ref('')
const showEditor = ref(false)
const editorFile = ref<any>(null)
const editorMode = ref<'text' | 'office'>('text')
const editorContent = ref('')
const editorLoading = ref(false)
const editorSaving = ref(false)
const editorError = ref('')
const editorTitle = ref('编辑文件')
let officeInstance: any = null
let officeScriptPromise: Promise<void> | null = null
const isPdfFile = (name?: string) => /\.pdf$/i.test(name || '')
const isOfficeFile = (name?: string) => /\.(docx?|xlsx?|pptx?)$/i.test(name || '')
const isTextFile = (name?: string) => /\.(txt|md|markdown|json|ya?ml|js|ts|tsx|jsx|vue|html?|css|scss|less|py|java|go|c|cpp|h|sh|sql|csv|log|conf|ini|xml|properties|env)$/i.test(name || '')
const releaseMediaPreviewUrl = () => {
  if (mediaPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(mediaPreviewUrl.value)
  mediaPreviewUrl.value = ''
}
const openMediaPreview = async (file: any, type: MediaPreviewType) => {
  releaseMediaPreviewUrl()
  mediaPreviewFile.value = file
  mediaPreviewType.value = type
  mediaPreviewError.value = ''
  mediaPreviewLoading.value = true
  mediaPreviewVisible.value = true
  try {
    const res = await projectApi.previewFile(file.id)
    if (!(res.data instanceof Blob) || !res.data.size) throw new Error('empty preview content')
    const responseType = String(res.headers?.['content-type'] || '').split(';')[0]
    const expectedType = String(file?.mimeType || '')
    const blob = res.data.type || (!responseType && !expectedType)
      ? res.data
      : new Blob([res.data], { type: responseType || expectedType })
    mediaPreviewUrl.value = URL.createObjectURL(blob)
  } catch {
    mediaPreviewError.value = '媒体内容读取失败，请确认登录状态、文件权限或下载原文件查看'
  } finally {
    mediaPreviewLoading.value = false
  }
}
const openPreview = async (file: any) => {
  const mediaType = getMediaPreviewType(file.fileName, file.mimeType)
  if (mediaType) return openMediaPreview(file, mediaType)
  if (isOfficeFile(file.fileName)) return openOfficeFile(file, 'view')
  previewFile.value = file
  previewUrl.value = null
  previewText.value = ''
  previewMessage.value = '该文件类型暂不支持浏览器内直接预览，可下载后查看'
  showPreview.value = true
  if (isTextFile(file.fileName)) {
    previewLoading.value = true
    try {
      const res = await projectApi.getFileContent(file.id)
      previewText.value = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
      previewMessage.value = ''
    } catch {
      previewMessage.value = '文件内容读取失败，请下载后查看'
    } finally {
      previewLoading.value = false
    }
    return
  }
  if (isPdfFile(file.fileName)) {
    previewLoading.value = true
    try {
      const res = await projectApi.downloadFile(file.id, false)
      const mime = 'application/pdf'
      const blob = new Blob([res.data], { type: mime })
      previewUrl.value = URL.createObjectURL(blob)
    } catch {
      previewMessage.value = '预览加载失败，请下载后查看'
    } finally {
      previewLoading.value = false
    }
  }
}
const closeGenericPreview = () => {
  if (previewUrl.value?.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = null
}
const downloadFile = async (file: any) => { if (!file) return; try { const res = await projectApi.downloadFile(file.id, true); const url = URL.createObjectURL(new Blob([res.data])); const a = document.createElement('a'); a.href = url; a.download = file.fileName; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url); if (selectedFile.value?.id === file.id) loadActivities() } catch { ElMessage.error('文件下载失败') } }
const openOfficeFile = async (file: any, mode: 'view' | 'edit') => { editorFile.value = file; editorTitle.value = mode === 'view' ? '查看文件' : '编辑文件'; editorError.value = ''; editorMode.value = 'office'; showEditor.value = true; editorLoading.value = true; try { const res = await projectApi.getOfficeConfig(file.id, mode); const { documentServerUrl, ...config } = res.data; await loadOfficeScript(documentServerUrl); nextTick(() => initOfficeEditor(config)) } catch { editorError.value = '打开 Office 在线查看器失败，请稍后重试。' } finally { editorLoading.value = false } }
const openEditor = async (file: any) => { if (isPdfFile(file.fileName)) return openPreview(file); if (isTextFile(file.fileName)) { editorFile.value = file; editorTitle.value = '编辑文件'; editorError.value = ''; editorMode.value = 'text'; editorContent.value = ''; showEditor.value = true; editorLoading.value = true; try { editorContent.value = (await projectApi.getFileContent(file.id)).data } catch { editorError.value = '读取文件内容失败' } finally { editorLoading.value = false }; return }; if (isOfficeFile(file.fileName)) return openOfficeFile(file, 'edit'); ElMessage.info('该文件类型暂不支持在线编辑') }
const saveContent = async () => { if (!editorFile.value) return; editorSaving.value = true; try { await projectApi.saveFileContent(editorFile.value.id, editorContent.value); ElMessage.success('文件内容已保存'); await loadAll() } finally { editorSaving.value = false } }
const closeEditor = () => { if (officeInstance?.destroy) { try { officeInstance.destroy() } catch { /* ignore */ } }; officeInstance = null; const container = document.getElementById('office-editor-container'); if (container) container.innerHTML = '' }
const loadOfficeScript = (serverUrl: string) => { if ((window as any).DocsAPI) return Promise.resolve(); if (officeScriptPromise) return officeScriptPromise; officeScriptPromise = new Promise<void>((resolve, reject) => { const script = document.createElement('script'); script.src = `${serverUrl}/web-apps/apps/api/documents/api.js`; const timeout = window.setTimeout(() => { officeScriptPromise = null; script.remove(); reject(new Error('OnlyOffice script load timeout')) }, 15000); script.onload = () => { window.clearTimeout(timeout); resolve() }; script.onerror = () => { window.clearTimeout(timeout); officeScriptPromise = null; reject(new Error('OnlyOffice script load failed')) }; document.head.appendChild(script) }); return officeScriptPromise }
const initOfficeEditor = (config: any) => { const tryInit = (attempt = 0) => { const container = document.getElementById('office-editor-container'); if (!container && attempt < 25) return setTimeout(() => tryInit(attempt + 1), 100); if (!container) return editorError.value = '在线编辑器容器未就绪'; try { config.events = { onError: (e: any) => { editorError.value = 'OnlyOffice 错误：' + (e?.message || e?.error || '未知错误') } }; officeInstance = new (window as any).DocsAPI.DocEditor('office-editor-container', config) } catch (e: any) { editorError.value = '在线编辑器初始化失败：' + (e?.message || e) } }; tryInit() }

const statusText = (s?: string) => ({ planning: '规划中', in_progress: '进行中', completed: '已完成', archived: '已归档' } as any)[s || ''] || s || '-'
const projectStatusType = (s?: string) => ({ planning: 'info', in_progress: 'primary', completed: 'success', archived: 'warning' } as any)[s || ''] || 'info'
const taskStatusType = (s?: string) => ({ pending: 'info', in_progress: 'primary', completed: 'success', cancelled: 'danger' } as any)[s || ''] || 'info'
const phaseStatusText = (s?: string) => ({ pending: '未开始', in_progress: '进行中', completed: '已完成' } as any)[s || ''] || s || '-'
const projectRoleText = (role?: string) => ({ leader: '负责人', member: '协作成员', viewer: '访客' } as any)[role || ''] || role || '成员'
const memberName = (id?: number) => members.value.find(item => item.userId === id)?.username || '未分配'
const uploaderName = (id?: number) => members.value.find(item => item.userId === id)?.username || (id ? `用户 #${id}` : '系统')
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatDate = (value?: string) => value ? new Date(value).toLocaleDateString('zh-CN') : '-'
const formatSize = (bytes?: number) => { if (!bytes) return '0 B'; const units = ['B', 'KB', 'MB', 'GB']; let value = bytes; let i = 0; while (value >= 1024 && i < units.length - 1) { value /= 1024; i++ }; return `${value.toFixed(i > 1 ? 1 : 0)} ${units[i]}` }
const fileExtension = (name?: string) => (name?.split('.').pop() || 'file').toLowerCase()
const activityText = (action: string) => ({ upload: '上传文件', download: '下载文件', update: '更新文件', edit: '打开编辑', view: '查看文件', move: '移动文件' } as any)[action] || '文件操作'
const activityType = (action: string) => ({ upload: 'primary', download: 'success', update: 'warning', edit: 'warning', view: 'info', move: 'primary' } as any)[action] || 'info'

onMounted(async () => {
  await loadAll()
  if (route.query.edit !== 'cover') return
  const query = { ...route.query }
  delete query.edit
  await router.replace({ query })
  if (authStore.hasPermission('project:edit') && authStore.hasPermission('project:cover_manage')) {
    await nextTick()
    await openEditProject()
  }
})
watch(mediaPreviewVisible, visible => { if (!visible) releaseMediaPreviewUrl() })
onBeforeUnmount(() => { if (candidateTimer) window.clearTimeout(candidateTimer); if (previewUrl.value?.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value); releaseEditCoverPreview(); releaseMediaPreviewUrl(); closeEditor() })
</script>

<style scoped>
.project-workbench { min-height: 100%; color: #1f2937; }
.project-heading { min-height: 76px; display: grid; grid-template-columns: 1fr minmax(360px, 1.3fr) 1fr; align-items: center; gap: 24px; margin: -8px 0 22px; }
.back-button { justify-self: start; color: #64748b; }.project-identity { text-align: center; min-width: 0; }.identity-line { display: flex; align-items: center; justify-content: center; gap: 10px; }.identity-line h1 { margin: 0; font-size: 24px; line-height: 1.25; color: #162033; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.version-switcher { min-height: 28px; display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 7px; color: #94a3b8; font-size: 12px; }.version-select { width: 112px; }.history-hint { color: #d97706; }.version-option { width: 100%; display: flex; align-items: center; gap: 10px; }.version-option strong { flex: 0 0 30px; }.version-change-log { min-width: 0; flex: 1; overflow: hidden; color: #94a3b8; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.version-default-state { color: #16a34a; font-size: 11px; }.heading-actions { justify-self: end; display: flex; gap: 9px; }
:global(.project-version-popper) { min-width: 320px !important; }:global(.project-version-popper .el-select-dropdown__item) { height: auto; min-height: 42px; padding: 6px 12px; }
.project-layout { display: grid; grid-template-columns: minmax(280px, 330px) minmax(0, 1fr); gap: 20px; align-items: start; }.project-sidebar { display: grid; gap: 18px; }.panel, .workspace-panel { background: rgba(255,255,255,.96); border: 1px solid rgba(226,232,240,.9); border-radius: 16px; box-shadow: 0 14px 38px rgba(15,23,42,.055); }.panel-header { min-height: 67px; padding: 16px 18px; display: flex; align-items: center; justify-content: space-between; gap: 10px; border-bottom: 1px solid #edf1f6; }.panel-header h2 { margin: 0; color: #1e293b; font-size: 16px; }.panel-header p { margin: 4px 0 0; color: #94a3b8; font-size: 11px; }.count-pill { display: inline-grid; place-items: center; min-width: 22px; height: 20px; padding: 0 6px; margin-left: 4px; border-radius: 999px; color: #1f8067; background: #eff9f5; font-size: 11px; }
.project-description { margin: 18px; padding: 14px; min-height: 48px; border-radius: 11px; color: #536176; background: #f8fafc; font-size: 13px; line-height: 1.65; }.info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 12px; padding: 0 18px 19px; }.info-grid div { display: grid; gap: 4px; }.info-grid span { color: #94a3b8; font-size: 11px; }.info-grid strong { color: #334155; font-size: 13px; }.snapshot-note { display: flex; align-items: center; gap: 7px; margin: -5px 18px 18px; padding: 10px; color: #b45309; background: #fffbeb; border-radius: 9px; font-size: 11px; }
.member-list { padding: 6px 16px 13px; max-height: 320px; overflow: auto; }.member-row { display: flex; align-items: center; gap: 10px; padding: 11px 2px; border-bottom: 1px solid #f0f3f7; }.member-row:last-child { border-bottom: 0; }.member-copy { min-width: 0; flex: 1; display: grid; gap: 3px; }.member-copy strong { color: #334155; font-size: 13px; }.member-copy span { color: #94a3b8; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workspace-panel { min-width: 0; overflow: hidden; }.workspace-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 20px; }.workspace-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: #e9edf3; }.workspace-tabs :deep(.el-tabs__item) { height: 65px; padding: 0 22px; }.workspace-tabs :deep(.el-tabs__content) { padding: 0; }.tab-label { display: inline-flex; align-items: center; gap: 8px; font-weight: 600; }.tab-label em { min-width: 22px; padding: 2px 6px; border-radius: 999px; background: #f1f5f9; color: #64748b; font-size: 10px; font-style: normal; }
.tab-toolbar, .file-toolbar { min-height: 66px; padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid #edf1f6; background: #fbfcfe; }.view-switch { display: flex; padding: 3px; border: 1px solid #e2e8f0; border-radius: 10px; background: #f1f5f9; }.view-switch button { border: 0; border-radius: 7px; padding: 7px 12px; color: #64748b; background: transparent; cursor: pointer; display: flex; align-items: center; gap: 6px; }.view-switch button.active { color: #1f8067; background: #fff; box-shadow: 0 2px 8px rgba(15,23,42,.08); }
.task-table { padding: 10px 20px 20px; }.task-title { display: grid; gap: 4px; }.task-title strong { color: #334155; }.task-title span { color: #94a3b8; font-size: 11px; }.priority-dot { padding: 4px 8px; border-radius: 999px; font-size: 11px; background: #f1f5f9; }.priority-dot.p3, .priority-dot.p4 { color: #dc2626; background: #fef2f2; }
.task-board { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; padding: 20px; background: #f8fafc; }.board-column { min-width: 0; padding: 12px; border: 1px solid #e7ebf2; border-radius: 13px; background: #f3f6fa; }.board-header { display: flex; align-items: center; justify-content: space-between; padding: 2px 3px 12px; }.board-header span { display: flex; align-items: center; gap: 8px; font-weight: 600; }.board-header i { width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; }.board-header i.in_progress { background: #2d9577; }.board-header i.completed { background: #22c55e; }.board-header em { color: #94a3b8; font-style: normal; }.board-list { display: grid; gap: 9px; }.task-card { display: grid; gap: 9px; padding: 13px; border: 1px solid #e5eaf1; border-radius: 11px; background: #fff; box-shadow: 0 5px 14px rgba(15,23,42,.035); }.task-card p { margin: 0; min-height: 32px; color: #64748b; font-size: 12px; line-height: 1.4; }.task-card > div { display: flex; justify-content: space-between; color: #94a3b8; font-size: 10px; }.board-empty { padding: 30px 0; text-align: center; color: #a4afbf; font-size: 12px; }
.schedule-calendar { padding: 0 18px 18px; }.schedule-calendar :deep(.el-calendar__header) { padding: 16px 4px; }.schedule-calendar :deep(.el-calendar-table .el-calendar-day) { height: 105px; padding: 5px; }.calendar-cell { display: grid; align-content: start; gap: 4px; height: 100%; overflow: auto; }.day-number { color: #64748b; font-size: 11px; }.calendar-task { border: 0; border-left: 3px solid #2d9577; border-radius: 4px; padding: 4px 5px; text-align: left; color: #1b735e; background: #eff9f5; font-size: 10px; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.calendar-task.completed { color: #15803d; border-color: #22c55e; background: #f0fdf4; }
.file-toolbar { align-items: flex-start; flex-wrap: wrap; }.breadcrumb-wrap { min-height: 34px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }.breadcrumb-wrap button { border: 0; color: #64748b; background: transparent; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; }.breadcrumb-wrap button:last-of-type { color: #1b735e; font-weight: 600; }.file-actions { display: flex; flex-wrap: wrap; gap: 7px; }.hidden-input { display: none; }.folder-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; padding: 18px 20px 0; }.folder-card { min-width: 0; display: flex; align-items: center; gap: 12px; border: 1px solid #e5eaf1; border-radius: 12px; padding: 12px; text-align: left; background: #fff; cursor: pointer; transition: .18s; }.folder-card:hover { transform: translateY(-2px); border-color: #b9dfd2; box-shadow: 0 8px 20px rgba(35,139,112,.08); }.folder-icon { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 10px; color: #1f8067; background: #eff9f5; font-size: 21px; }.folder-card > span:nth-child(2) { min-width: 0; flex: 1; display: grid; gap: 3px; }.folder-card strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.folder-card small { color: #94a3b8; }.folder-chevron { color: #b2bdcc; }
.file-list { margin: 18px 20px 22px; border: 1px solid #e8ecf2; border-radius: 13px; overflow: hidden; }.file-list-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 11px 14px; color: #7b8799; background: #f8fafc; font-size: 11px; font-weight: 600; }.file-head-labels { display: grid; grid-template-columns: minmax(260px, 1fr) 80px 130px minmax(250px, auto); align-items: center; gap: 14px; flex: 1; min-width: 0; }.file-row { display: grid; grid-template-columns: minmax(260px, 1fr) 80px 130px minmax(250px, auto); align-items: center; gap: 14px; padding: 11px 14px; }.file-row { min-height: 68px; border-top: 1px solid #edf1f5; background: #fff; }.file-row:hover { background: #fbfdff; }.file-name-cell { min-width: 0; display: flex; align-items: center; gap: 11px; }.file-type-icon { width: 42px; height: 42px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 10px; color: #1f8067; background: #eff9f5; font-size: 9px; font-weight: 700; }.file-type-icon.pdf { color: #dc2626; background: #fef2f2; }.file-type-icon.png, .file-type-icon.jpg, .file-type-icon.jpeg { color: #9333ea; background: #faf5ff; }.file-name-cell > span:last-child { min-width: 0; display: grid; gap: 4px; }.file-name-cell strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-name-cell small, .file-time { color: #94a3b8; font-size: 11px; }.file-version { color: #1f8067; font-size: 12px; font-weight: 600; }.row-actions { display: flex; align-items: center; flex-wrap: wrap; }.row-actions .el-button { margin-left: 0; padding: 5px 7px; }.file-list.empty { min-height: 220px; }
.file-view-switch { display: flex; padding: 2px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; flex-shrink: 0; }.file-view-switch button { border: 0; border-radius: 6px; padding: 4px 8px; color: #94a3b8; background: transparent; cursor: pointer; display: grid; place-items: center; font-size: 14px; }.file-view-switch button.active { color: #1f8067; background: #eff9f5; }.file-list.grid-view { overflow: visible; }.file-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; padding: 16px; }.file-card { display: grid; gap: 10px; padding: 14px; border: 1px solid #e5eaf1; border-radius: 12px; background: #fff; transition: .18s; }.file-card:hover { transform: translateY(-2px); border-color: #b9dfd2; box-shadow: 0 8px 20px rgba(35,139,112,.08); }.file-card-top { display: flex; align-items: center; gap: 10px; min-width: 0; }.file-card-info { min-width: 0; display: grid; gap: 3px; }.file-card-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #1e293b; }.file-card-info small { color: #94a3b8; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-card-meta { display: flex; align-items: center; justify-content: space-between; padding-top: 8px; border-top: 1px solid #f0f3f7; }.file-card-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 2px; }.file-card-actions .el-button { margin-left: 0; padding: 4px 6px; }
.phase-list { display: grid; gap: 12px; padding: 20px; }.phase-card { display: flex; align-items: center; gap: 16px; padding: 16px; border: 1px solid #e7ebf1; border-radius: 13px; }.phase-index { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 11px; color: #1f8067; background: #eff9f5; font-weight: 700; }.phase-copy { flex: 1; display: grid; gap: 4px; }.phase-copy p { margin: 0; color: #64748b; font-size: 12px; }.phase-copy span { color: #94a3b8; font-size: 10px; }
.project-model-toolbar { min-height: 66px; display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 12px 20px; border-bottom: 1px solid #edf1f6; background: #fbfcfe; }.project-model-toolbar > div { display: grid; gap: 4px; }.project-model-toolbar span { color: #94a3b8; font-size: 11px; }.project-model-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(210px,1fr)); gap: 14px; padding: 20px; }.project-model-card { overflow: hidden; border: 1px solid #e5eaf1; border-radius: 13px; background: #fff; cursor: pointer; transition: .18s; }.project-model-card:hover { transform: translateY(-3px); border-color: #9bcfbe; box-shadow: 0 12px 26px rgba(35,139,112,.09); }.project-model-cover { position: relative; height: 145px; overflow: hidden; background: #0b1729; }.project-model-cover img { width: 100%; height: 100%; object-fit: cover; }.project-model-cover > div { height: 100%; display: grid; place-items: center; color: #64748b; font-size: 35px; }.project-model-cover .el-tag { position: absolute; top: 8px; right: 8px; }.unlink-model { position: absolute; top: 8px; left: 8px; z-index: 3; opacity: 0; color: #dc2626; background: rgba(255,255,255,.94); transition: opacity .18s; }.project-model-card:hover .unlink-model { opacity: 1; }.project-model-copy { display: grid; gap: 7px; padding: 12px; }.project-model-copy strong { overflow: hidden; color: #1e293b; text-overflow: ellipsis; white-space: nowrap; }.project-model-copy > div { display: flex; gap: 4px; min-height: 20px; }.project-model-copy > div span { padding: 2px 5px; color: #1f8067; background: #eff9f5; border-radius: 5px; font-size: 9px; }.project-model-copy small { color: #94a3b8; }
:global(.file-detail-drawer .el-drawer__header) { margin: 0; padding: 20px; border-bottom: 1px solid #e8edf4; }:global(.file-detail-drawer .el-drawer__body) { padding: 0 20px 78px; background: #f7f9fc; }.drawer-heading { width: 100%; display: flex; align-items: center; justify-content: space-between; }.drawer-heading > div { min-width: 0; display: grid; gap: 4px; }.drawer-heading span { color: #94a3b8; font-size: 11px; }.drawer-heading strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.drawer-search { position: sticky; top: 0; z-index: 2; padding: 15px 0 12px; background: #f7f9fc; }.file-summary-card { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; overflow: hidden; border: 1px solid #e5eaf1; border-radius: 12px; background: #e5eaf1; }.file-summary-card div { display: grid; gap: 5px; padding: 13px; background: #fff; }.file-summary-card span { color: #94a3b8; font-size: 10px; }.file-summary-card strong { color: #334155; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.drawer-section-title { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 7px; font-weight: 600; }.activity-timeline { padding: 8px 0 0 5px; }.activity-card { padding: 11px; border: 1px solid #e6ebf2; border-radius: 10px; background: #fff; }.activity-card p { margin: 5px 0 0; color: #64748b; font-size: 12px; }.drawer-footer { position: absolute; inset: auto 0 0; display: flex; justify-content: flex-end; gap: 8px; padding: 13px 20px; border-top: 1px solid #e5eaf1; background: #fff; }
.dialog-form .el-select, .dialog-form .el-date-editor { width: 100%; }.form-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }.edit-cover-field { width: 100%; display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 14px; padding: 12px; border: 1px solid #e3ebe8; border-radius: 12px; background: #f8fbfa; }.edit-cover-preview { height: 116px; display: grid; place-items: center; overflow: hidden; border: 1px solid #d9e6e1; border-radius: 9px; background: #edf4f1; }.edit-cover-preview img { width: 100%; height: 100%; display: block; object-fit: cover; }.edit-cover-placeholder { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 7px; color: #82958e; font-size: 11px; }.edit-cover-placeholder .el-icon { color: #3c967a; font-size: 25px; }.edit-cover-controls { min-width: 0; display: flex; align-items: flex-start; justify-content: center; flex-direction: column; gap: 8px; }.edit-cover-buttons { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }.edit-cover-buttons .el-button { margin-left: 0; }.edit-cover-controls p { margin: 0; color: #8a9b95; font-size: 11px; line-height: 1.55; }.edit-cover-controls > span { max-width: 100%; overflow: hidden; color: #526b61; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.member-dialog-tools { display: grid; grid-template-columns: 1fr 160px; gap: 12px; }.candidate-list { min-height: 250px; max-height: 390px; display: grid; gap: 8px; align-content: start; overflow: auto; margin-top: 14px; }.candidate-list button { display: flex; align-items: center; gap: 12px; padding: 11px; border: 1px solid #e5eaf1; border-radius: 11px; text-align: left; background: #fff; cursor: pointer; }.candidate-list button.selected { border-color: #62b599; background: #eff9f5; box-shadow: 0 0 0 2px rgba(35, 139, 112, .08); }.candidate-list button > span { min-width: 0; flex: 1; display: grid; gap: 4px; }.candidate-list small { color: #94a3b8; }.candidate-list button > .el-icon { color: #1f8067; font-size: 20px; }.candidate-selection-count { margin-right: auto; color: #64748b; font-size: 12px; }.model-link-tools { display: grid; grid-template-columns: 1fr auto; gap: 10px; }.model-candidate-grid { min-height: 260px; max-height: 450px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; align-content: start; margin-top: 14px; overflow: auto; padding: 2px; }.model-candidate-grid > button { min-width: 0; display: flex; align-items: center; gap: 12px; padding: 10px; border: 1px solid #e4eaf2; border-radius: 12px; text-align: left; background: #fff; cursor: pointer; transition: .18s; }.model-candidate-grid > button:hover { border-color: #9bcfbe; }.model-candidate-grid > button.selected { border-color: #2d9577; background: #eff9f5; box-shadow: 0 0 0 2px rgba(35,139,112,.08); }.candidate-cover { width: 72px; height: 54px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; color: #94a3b8; border-radius: 8px; background: #0b1729; }.candidate-cover img { width: 100%; height: 100%; object-fit: cover; }.candidate-copy { min-width: 0; flex: 1; display: grid; gap: 5px; }.candidate-copy strong, .candidate-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.candidate-copy small { color: #94a3b8; font-size: 11px; }.candidate-check { flex: 0 0 auto; color: #1f8067; font-size: 20px; }.folder-location { color: #94a3b8; font-size: 12px; }.feedback-form { margin-top: 16px; }
.preview-body { height: 70vh; display: grid; place-items: center; overflow: auto; background: #f3f5f8; }.preview-body img { max-width: 100%; max-height: 100%; object-fit: contain; }.preview-body video { max-width: 100%; max-height: 100%; }.preview-body iframe { width: 100%; height: 100%; border: 0; }.preview-text { width: 100%; height: 100%; margin: 0; padding: 16px; box-sizing: border-box; text-align: left; white-space: pre-wrap; word-break: break-all; font-family: Consolas, Monaco, monospace; font-size: 13px; line-height: 1.6; color: #1e293b; background: #fff; border-radius: 8px; overflow: auto; }.preview-unsupported { display: grid; place-items: center; gap: 16px; }.editor-body { height: calc(100vh - 150px); display: flex; flex-direction: column; }.text-editor, .office-editor { flex: 1; min-height: 0; }.text-editor :deep(.el-textarea__inner) { height: 100% !important; resize: none; font-family: Consolas, Monaco, monospace; }.office-editor { overflow: hidden; border: 1px solid #dce2eb; border-radius: 8px; }
@media (max-width: 1100px) { .project-heading { grid-template-columns: auto 1fr; }.project-identity { grid-column: 1 / -1; grid-row: 1; }.back-button { grid-row: 2; }.heading-actions { grid-row: 2; }.project-layout { grid-template-columns: 1fr; }.project-sidebar { grid-template-columns: 1fr 1fr; }.task-board { grid-template-columns: 1fr; }.file-list-head { display: none; }.file-row { grid-template-columns: 1fr auto; }.file-time, .file-version { display: none; } }
@media (max-width: 720px) { .project-sidebar { grid-template-columns: 1fr; }.heading-actions .el-button:first-child { display: none; }.file-actions { width: 100%; }.task-board { padding: 12px; }.form-columns, .edit-cover-field { grid-template-columns: 1fr; }.edit-cover-preview { height: 150px; }.member-dialog-tools, .model-candidate-grid { grid-template-columns: 1fr; } }
</style>
