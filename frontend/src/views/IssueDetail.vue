<template>
  <div class="page-shell issue-detail-page" v-loading="loading">
    <section v-if="detail" class="section-card issue-summary">
      <div class="summary-main">
        <div class="issue-badge">{{ priorityText }}</div>
        <div class="summary-copy">
          <div class="crumb">问题台账 / 问题详情</div>
          <div class="summary-title-row">
            <span class="issue-no mono">{{ detail.issueNo }}</span>
            <h1>{{ valueText(detail.itemTitle) }}</h1>
          </div>
          <div class="summary-meta">
            <span>客户：{{ valueText(detail.customerName) }}</span>
            <span>项目：{{ displayProjectName(detail.projectName) }}</span>
            <span>区域：{{ locationText }}</span>
            <span>反馈人：{{ valueText(detail.reporterName) }}</span>
            <span>创建：{{ formatDateTime(detail.createTime || detail.receivedAt) }}</span>
            <span>更新：{{ formatDateTime(detail.updateTime || detail.createTime || detail.receivedAt) }}</span>
          </div>
        </div>
      </div>
      <div class="summary-actions">
        <div class="status-stack">
          <span>状态</span>
          <el-tag size="large" :type="statusMeta(detail.currentStatus).type">{{ statusMeta(detail.currentStatus).label }}</el-tag>
        </div>
        <div class="action-row">
          <el-button @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <el-button @click="activeTab = 'process'">追加记录</el-button>
          <el-button v-if="detail.currentStatus !== 'CLOSED'" type="primary" @click="openCloseDialog">标记闭环</el-button>
          <el-button v-if="detail.currentStatus === 'CLOSED'" @click="openReopenDialog">重新打开</el-button>
        </div>
      </div>
    </section>

    <section v-if="detail" class="completion-strip" :class="{ warning: completionStatus.needsClosedWarning }">
      <div class="completion-score">
        <strong>{{ completionStatus.score }}%</strong>
        <span>资料完善度</span>
      </div>
      <div class="completion-copy">
        <span>已完成：{{ completionStatus.done.length ? completionStatus.done.join('、') : '暂无' }}</span>
        <span>待完善：{{ completionStatus.todo.length ? completionStatus.todo.join('、') : '暂无' }}</span>
      </div>
      <el-alert
        v-if="completionStatus.needsClosedWarning"
        title="问题已关闭，但处理结论或预防沉淀仍不完整"
        type="warning"
        show-icon
        :closable="false"
      />
    </section>

    <section v-if="detail" class="detail-workspace">
      <main ref="detailMainRef" class="detail-main section-card">
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="问题概览" name="overview">
            <div v-if="activeTab === 'overview'" class="tab-page">
              <div class="tab-head">
                <div>
                  <div class="section-title marked">问题概览</div>
                  <p class="section-hint">先完整阅读现场现象、附件和基础信息，需要调整时再进入编辑。</p>
                </div>
                <el-button type="primary" plain @click="openEditDialog('definition')">
                  <el-icon><EditPen /></el-icon>
                  编辑本区
                </el-button>
              </div>

              <div class="overview-stack">
                <section class="overview-section">
                  <div class="section-line-head">
                    <span class="section-icon blue"><el-icon><Document /></el-icon></span>
                    <div>
                      <div class="block-title">问题现象</div>
                      <p class="mini-hint">现场反馈、判断依据和当前可见影响</p>
                    </div>
                  </div>
                  <p class="readable-text lead-text">{{ valueText(detail.description || detail.itemTitle) }}</p>
                </section>

                <section class="overview-section">
                  <div class="section-line-head">
                    <span class="section-icon violet"><el-icon><Location /></el-icon></span>
                    <div>
                      <div class="block-title">影响范围</div>
                      <p class="mini-hint">受影响区域、设备或业务范围</p>
                    </div>
                  </div>
                  <p class="readable-text">{{ valueText(detail.impactScope) }}</p>
                </section>

                <section class="overview-section attachment-section">
                  <div class="section-line-head">
                    <span class="section-icon green"><el-icon><Picture /></el-icon></span>
                    <div>
                      <div class="block-title">图片/附件（{{ attachments.length }}）</div>
                      <p class="mini-hint">现场截图、照片或补充材料</p>
                    </div>
                  </div>
                  <div v-if="attachments.length" class="overview-file-list">
                    <div v-for="attachment in attachments.slice(0, 4)" :key="attachment.id" class="overview-file-row">
                      <el-image
                        v-if="isImageAttachment(attachment)"
                        :src="attachment.previewUrl"
                        :preview-src-list="imagePreviewUrls"
                        fit="cover"
                        preview-teleported
                        class="overview-file-thumb"
                      />
                      <div v-else class="overview-file-thumb file-thumb-fallback">
                        <el-icon><Document /></el-icon>
                      </div>
                      <div class="overview-file-meta">
                        <strong :title="attachment.fileName">{{ attachment.fileName }}</strong>
                        <span>{{ fileTypeLabel(attachment) }} · {{ formatFileSize(attachment.fileSize) }}</span>
                        <small>上传：{{ formatDateTime(attachment.createTime || attachment.uploadTime) }}</small>
                      </div>
                      <el-button link type="primary" @click="openAttachment(attachment)">预览</el-button>
                    </div>
                  </div>
                  <el-empty v-else description="暂无附件" :image-size="72" />
                </section>

                <section class="overview-section">
                  <div class="section-line-head">
                    <span class="section-icon cyan"><el-icon><InfoFilled /></el-icon></span>
                    <div>
                      <div class="block-title">基本信息</div>
                      <p class="mini-hint">来源、分类、位置、设备与责任人</p>
                    </div>
                  </div>
                  <div class="info-grid">
                    <div v-for="item in overviewPairs" :key="item.label" class="info-pair">
                      <span>{{ item.label }}</span>
                      <strong>{{ item.value }}</strong>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="处理过程" name="process">
            <div v-if="activeTab === 'process'" class="tab-page">
              <div class="tab-head">
                <div>
                  <div class="section-title marked">处理过程</div>
                  <p class="section-hint">追加排查、处理措施、沟通结论或临时方案，记录会同步进入右侧时间线。</p>
                </div>
              </div>

              <div class="process-tab-grid">
                <div class="process-left">
                  <el-form :model="recordForm" label-position="top" class="record-compose">
                    <div class="section-line-head">
                      <span class="section-icon blue"><el-icon><Memo /></el-icon></span>
                      <div>
                        <div class="block-title">追加处理记录</div>
                        <p class="mini-hint">记录本次排查、处理措施、沟通结论或临时方案</p>
                      </div>
                    </div>
                    <el-form-item label="处理记录">
                      <el-input v-model="recordForm.content" type="textarea" :rows="8" maxlength="1000" show-word-limit placeholder="请详细描述本次处理过程、采取的措施及沟通结论..." />
                    </el-form-item>
                    <div class="record-actions">
                      <el-button :loading="uploadingAttachment" @click="triggerAttachmentSelect">
                        <el-icon><Upload /></el-icon>
                        上传附件
                      </el-button>
                      <el-button @click="recordForm.content = '已完成现场复核，正在同步处理结果和后续措施。'">
                        <el-icon><Tickets /></el-icon>
                        填入常用记录
                      </el-button>
                      <el-button type="primary" :loading="submittingRecord" @click="submitRecord">
                        <el-icon><Promotion /></el-icon>
                        提交记录
                      </el-button>
                    </div>
                    <div class="record-tips">
                      <div class="tips-title">
                        <el-icon><InfoFilled /></el-icon>
                        填写提示
                      </div>
                      <ul>
                        <li>说明问题现象的最新状态或变化</li>
                        <li>记录采取的处理措施与验证结果</li>
                        <li>记录与相关人员的沟通结论</li>
                        <li>如有临时方案，请明确后续跟进计划</li>
                      </ul>
                    </div>
                  </el-form>

                  <section class="process-suggestion-card">
                    <div class="side-card-head slim">
                      <div class="section-line-head compact">
                        <span class="section-icon green"><el-icon><Opportunity /></el-icon></span>
                        <div class="block-title">处理建议 / 最近操作提示</div>
                      </div>
                      <span class="muted">同步时间线</span>
                    </div>
                    <div class="suggestion-grid">
                      <div>
                        <strong>建议下一步行动</strong>
                        <p>确认 {{ valueText(detail.devicePoint || detail.systemType || '相关设备') }} 稳定性，持续观察恢复情况并验证控制策略。</p>
                      </div>
                      <div>
                        <strong>最近操作</strong>
                        <ul v-if="timelineRecords.length">
                          <li v-for="record in timelineRecords.slice(0, 2)" :key="record.id">
                            {{ formatDateTime(record.operateTime) }}　{{ record.operatorName || '系统' }} {{ recordActionLabel(record.actionType) }}
                          </li>
                        </ul>
                        <p v-else>暂无最近操作。</p>
                      </div>
                    </div>
                  </section>
                </div>

                <div class="history-panel">
                  <div class="side-card-head slim">
                    <div class="section-line-head compact">
                      <span class="section-icon cyan"><el-icon><Clock /></el-icon></span>
                      <div class="block-title">历史处理记录</div>
                    </div>
                    <el-tag size="small" effect="plain">{{ processRecords.length }} 条</el-tag>
                  </div>
                  <div class="process-list compact">
                    <div v-for="record in processRecords" :key="record.id" class="history-record-card">
                      <div class="record-card-head">
                        <el-avatar :size="34">{{ (record.operatorName || '系').slice(0, 1) }}</el-avatar>
                        <div class="record-person">
                          <strong>{{ record.operatorName || '系统' }}</strong>
                          <span>{{ recordActionLabel(record.actionType) }}</span>
                        </div>
                        <time>{{ formatDateTime(record.operateTime) }}</time>
                      </div>
                      <p class="readable-text">{{ valueText(record.content) }}</p>
                      <el-tag size="small" type="primary" effect="plain">{{ recordActionLabel(record.actionType) }}</el-tag>
                    </div>
                    <el-empty v-if="!processRecords.length" description="暂无处理记录" :image-size="72" />
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="原因与结论" name="cause">
            <div v-if="activeTab === 'cause'" class="tab-page">
              <div class="tab-head">
                <div>
                  <div class="section-title marked">原因与结论</div>
                  <p class="section-hint">归因、内部结论和客户反馈口径集中展示，避免分散在多个输入框里。</p>
                </div>
                <el-button type="primary" plain @click="openEditDialog('cause')">
                  <el-icon><EditPen /></el-icon>
                  编辑本区
                </el-button>
              </div>

              <div class="cause-layout">
                <section class="insight-card reason-category-card">
                  <div class="section-line-head">
                    <span class="section-icon blue"><el-icon><CollectionTag /></el-icon></span>
                    <div class="block-title">归因分类</div>
                  </div>
                  <el-tag size="large" effect="plain" type="primary">{{ valueText(detail.causeCategory) }}</el-tag>
                  <p class="readable-text">{{ valueText(detail.internalConclusion || detail.latestProgress || detail.causeDetail) }}</p>
                </section>

                <section class="insight-card reason-detail-card">
                  <div class="section-line-head">
                    <span class="section-icon cyan"><el-icon><QuestionFilled /></el-icon></span>
                    <div class="block-title">原因说明</div>
                  </div>
                  <ol v-if="hasText(detail.causeDetail)" class="reason-list">
                    <li v-for="(line, index) in splitDisplayLines(detail.causeDetail)" :key="`${index}-${line}`">{{ line }}</li>
                  </ol>
                  <p v-else class="readable-text">-</p>
                </section>

                <section class="insight-card">
                  <div class="section-line-head">
                    <span class="section-icon violet"><el-icon><Finished /></el-icon></span>
                    <div class="block-title">内部处理结论</div>
                  </div>
                  <p class="readable-text">{{ valueText(detail.internalConclusion || detail.latestProgress) }}</p>
                </section>

                <section class="insight-card">
                  <div class="section-line-head">
                    <span class="section-icon green"><el-icon><ChatDotRound /></el-icon></span>
                    <div class="block-title">客户反馈口径</div>
                  </div>
                  <p class="readable-text">{{ valueText(detail.customerFeedback) }}</p>
                </section>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="预防与知识沉淀" name="prevention">
            <div v-if="activeTab === 'prevention'" class="tab-page">
              <div class="tab-head">
                <div>
                  <div class="section-title marked">预防与知识沉淀</div>
                  <p class="section-hint">沉淀预防建议、后续措施和复用标签，便于类似问题快速定位。</p>
                </div>
                <el-button type="primary" plain @click="openEditDialog('prevention')">
                  <el-icon><EditPen /></el-icon>
                  编辑本区
                </el-button>
              </div>

              <div class="prevention-layout">
                <section class="insight-card">
                  <div class="section-line-head">
                    <span class="section-icon blue"><el-icon><BellFilled /></el-icon></span>
                    <div>
                      <div class="block-title">预防建议</div>
                      <p class="mini-hint">减少同类问题复发的建议</p>
                    </div>
                  </div>
                  <p class="readable-text">{{ valueText(detail.preventiveAction) }}</p>
                  <div v-if="hasText(detail.preventiveAction)" class="suggestion-note">
                    <strong>建议落地要点</strong>
                    <ul>
                      <li>明确监测指标和触发条件</li>
                      <li>同步到巡检或值班流程</li>
                      <li>优先处理高频复发环节</li>
                    </ul>
                  </div>
                </section>

                <section class="insight-card">
                  <div class="section-line-head">
                    <span class="section-icon green"><el-icon><Tools /></el-icon></span>
                    <div>
                      <div class="block-title">后续措施</div>
                      <p class="mini-hint">待跟进动作和预期效果</p>
                    </div>
                  </div>
                  <ol v-if="hasText(detail.followUpAction)" class="action-list">
                    <li v-for="(line, index) in splitDisplayLines(detail.followUpAction)" :key="`${index}-${line}`">{{ line }}</li>
                  </ol>
                  <p v-else class="readable-text">-</p>
                  <div class="expected-result">
                    <el-icon><CircleCheck /></el-icon>
                    提升系统可观测性与运维响应效率，降低同类异常风险。
                  </div>
                </section>

                <section class="insight-card">
                  <div class="section-line-head">
                    <span class="section-icon violet"><el-icon><CollectionTag /></el-icon></span>
                    <div class="block-title">复用标签</div>
                  </div>
                  <div v-if="reuseTagList.length" class="inline-tags pill-tags">
                    <el-tag v-for="tag in reuseTagList" :key="tag" effect="plain">{{ tag }}</el-tag>
                    <el-button size="small" plain>添加标签</el-button>
                  </div>
                  <p v-else class="readable-text">-</p>
                </section>

                <section class="insight-card">
                  <div class="section-line-head">
                    <span class="section-icon cyan"><el-icon><Reading /></el-icon></span>
                    <div class="block-title">知识沉淀</div>
                  </div>
                  <div class="knowledge-state" :class="{ muted: detail.knowledgeIncluded === 0 }">
                    <el-icon><CircleCheck /></el-icon>
                    <div>
                      <strong>{{ detail.knowledgeIncluded === 0 ? '暂不纳入知识沉淀' : '已纳入知识沉淀' }}</strong>
                      <p>{{ detail.knowledgeIncluded === 0 ? '该问题暂不进入知识库，可后续补充。' : '该问题已进入知识库，可被检索与复用。' }}</p>
                    </div>
                  </div>
                </section>

                <section class="insight-card wide-block">
                  <div class="section-line-head">
                    <span class="section-icon blue"><el-icon><ChatDotRound /></el-icon></span>
                    <div class="block-title">备注</div>
                  </div>
                  <div class="note-row">
                    <p class="readable-text">{{ valueText(detail.notes) }}</p>
                    <el-button link type="primary" @click="openEditDialog('prevention')">编辑</el-button>
                  </div>
                </section>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </main>

      <aside class="side-rail" :style="sideRailStyle">
        <article class="section-card side-card">
          <div class="side-card-head">
            <div class="section-title marked">处理时间线</div>
            <el-button link type="primary" @click="activeTab = 'process'">全部记录</el-button>
          </div>
          <div v-if="timelineRecords.length" class="side-timeline">
            <div v-for="record in timelineRecords" :key="record.id" class="side-timeline-item">
              <span class="timeline-dot" :class="{ success: record.toStatus === 'CLOSED' }"></span>
              <div>
                <strong>{{ recordActionLabel(record.actionType) }}</strong>
                <p>{{ valueText(record.content) }}</p>
                <time>{{ formatDateTime(record.operateTime) }}</time>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无时间线" :image-size="60" />
        </article>

        <details class="section-card side-card side-fold-card">
          <summary class="side-card-head">
            <div class="section-title marked">相似历史问题</div>
            <span class="side-fold-meta">
              <span class="muted">{{ detail.similarIssues?.length || 0 }} 条</span>
              <el-icon class="fold-icon"><ArrowDown /></el-icon>
            </span>
          </summary>
          <div class="side-fold-body">
            <div v-if="detail.similarIssues?.length" class="similar-list">
              <button v-for="item in detail.similarIssues" :key="item.id" type="button" class="similar-item" @click="goSimilarIssue(item)">
                <span class="mono">{{ item.issueNo }}</span>
                <strong>{{ valueText(item.itemTitle) }}</strong>
                <small>{{ (item.matchReasons || []).join('、') || statusMeta(item.currentStatus).label }}</small>
              </button>
            </div>
            <el-empty v-else description="暂无相似问题" :image-size="60" />
          </div>
        </details>

        <details class="section-card side-card side-fold-card">
          <summary class="side-card-head">
            <div class="section-title marked">附件摘要</div>
            <span class="side-fold-meta">
              <span class="muted">{{ attachments.length }} 个</span>
              <el-icon class="fold-icon"><ArrowDown /></el-icon>
            </span>
          </summary>
          <div class="side-fold-body">
            <input ref="attachmentInputRef" class="hidden-file-input" type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" multiple @change="handleAttachmentChange" />
            <div class="side-upload-row">
              <el-button size="small" type="primary" plain :loading="uploadingAttachment" @click="triggerAttachmentSelect">
                <el-icon><Upload /></el-icon>
                补传
              </el-button>
            </div>
            <div class="attachment-summary">
              <div class="summary-count">
                <strong>{{ attachments.length }}</strong>
                <span>全部附件</span>
              </div>
              <div class="summary-count">
                <strong>{{ imageAttachments.length }}</strong>
                <span>图片</span>
              </div>
            </div>
            <div v-if="attachments.length" class="file-list">
              <div v-for="attachment in attachments.slice(0, 5)" :key="attachment.id" class="file-row rich">
                <el-image
                  v-if="isImageAttachment(attachment)"
                  :src="attachment.previewUrl"
                  :preview-src-list="imagePreviewUrls"
                  fit="cover"
                  preview-teleported
                  class="side-file-thumb"
                />
                <div v-else class="side-file-thumb file-thumb-fallback">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="side-file-copy">
                  <strong :title="attachment.fileName">{{ attachment.fileName }}</strong>
                  <span>{{ fileTypeLabel(attachment) }} · {{ formatFileSize(attachment.fileSize) }}</span>
                </div>
                <button type="button" title="预览" @click="openAttachment(attachment)">
                  <el-icon><Download /></el-icon>
                </button>
                <button type="button" title="删除" @click="removeAttachment(attachment)">删除</button>
              </div>
            </div>
            <el-empty v-else description="暂无附件" :image-size="60" />
          </div>
        </details>
      </aside>
    </section>

    <el-dialog v-model="editDialogVisible" :title="editDialogTitle" width="760px" class="edit-dialog">
      <el-form label-position="top" class="section-form">
        <template v-if="editMode === 'definition'">
          <div class="definition-grid">
            <el-form-item label="反馈人"><el-input v-model="definitionForm.reporterName" /></el-form-item>
            <el-form-item label="收到反馈时间"><el-date-picker v-model="definitionForm.receivedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
            <el-form-item label="来源渠道">
              <el-select v-model="definitionForm.source" filterable allow-create default-first-option>
                <el-option v-for="item in sourceOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="问题大类">
              <el-select v-model="definitionForm.categoryPath" filterable allow-create default-first-option>
                <el-option v-for="item in problemCategoryOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="建筑/楼栋"><el-input v-model="definitionForm.buildingName" /></el-form-item>
            <el-form-item label="楼层"><el-input v-model="definitionForm.floorName" /></el-form-item>
            <el-form-item label="区域/房间"><el-input v-model="definitionForm.areaName" /></el-form-item>
            <el-form-item label="系统/设备类型">
              <el-select v-model="definitionForm.systemType" filterable allow-create default-first-option>
                <el-option v-for="item in systemTypeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="设备编号/点位"><el-input v-model="definitionForm.devicePoint" /></el-form-item>
            <el-form-item label="紧急程度">
              <el-select v-model="definitionForm.priority" clearable placeholder="请选择">
                <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="问题标题"><el-input v-model="definitionForm.itemTitle" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="问题现象"><el-input v-model="definitionForm.description" type="textarea" :rows="5" maxlength="1000" show-word-limit /></el-form-item>
          <el-form-item label="影响范围"><el-input v-model="definitionForm.impactScope" type="textarea" :rows="3" /></el-form-item>
        </template>

        <template v-else-if="editMode === 'cause'">
          <el-form-item label="归因分类">
            <el-select v-model="causeForm.causeCategory" clearable placeholder="请选择">
              <el-option v-for="item in attributionOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="原因说明"><el-input v-model="causeForm.causeDetail" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="内部处理结论"><el-input v-model="causeForm.internalConclusion" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="客户反馈口径"><el-input v-model="causeForm.customerFeedback" type="textarea" :rows="3" /></el-form-item>
        </template>

        <template v-else>
          <el-form-item label="预防建议"><el-input v-model="preventionForm.preventiveAction" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="后续措施"><el-input v-model="preventionForm.followUpAction" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="复用标签"><el-input v-model="preventionForm.reuseTags" placeholder="多个标签用逗号分隔，例如：网关离线, 485异常" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="preventionForm.notes" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="纳入知识沉淀"><el-switch v-model="preventionForm.knowledgeIncluded" :active-value="1" :inactive-value="0" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCurrentEdit" @click="submitEditDialog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeDialogVisible" title="标记闭环" width="760px">
      <el-form ref="closeFormRef" :model="closeForm" :rules="closeRules" label-position="top" class="close-form">
        <el-form-item label="处理结论" prop="content">
          <el-input v-model="closeForm.content" type="textarea" :rows="3" placeholder="写清本次如何解决、验证结果是什么" />
        </el-form-item>
        <div class="dialog-grid">
          <el-form-item label="归因分类" prop="causeCategory">
            <el-select v-model="closeForm.causeCategory" placeholder="请选择">
              <el-option v-for="item in attributionOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="复用标签">
            <el-input v-model="closeForm.reuseTags" placeholder="多个标签用逗号分隔" />
          </el-form-item>
        </div>
        <el-form-item label="原因说明">
          <el-input v-model="closeForm.causeDetail" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="客户反馈口径">
          <el-input v-model="closeForm.customerFeedback" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="预防建议">
          <el-input v-model="closeForm.preventiveAction" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="后续措施">
          <el-input v-model="closeForm.followUpAction" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="是否纳入知识沉淀">
          <el-switch v-model="closeForm.knowledgeIncluded" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingClose" @click="submitClose">确认闭环</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reopenDialogVisible" title="重新打开问题" width="560px">
      <el-form ref="reopenFormRef" :model="reopenForm" :rules="reopenRules" label-position="top">
        <el-form-item label="重开原因" prop="reason">
          <el-input v-model="reopenForm.reason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reopenDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingReopen" @click="submitReopen">确认重开</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addIssueRecord, closeIssue, deleteIssueAttachment, getIssueAttachments, getIssueDetail, reopenIssue, updateIssue, uploadIssueAttachment } from '@/api/issues'
import { getRuleOptions } from '@/api/ruleOptions'
import { formatDateTime, statusMeta } from '@/utils/format'

const actionTypeMap = {
  IMPORTED: '导入',
  CREATED: '创建',
  FOLLOW_UP: '跟进',
  CLOSE: '闭环',
  REOPEN: '重新打开'
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submittingRecord = ref(false)
const submittingClose = ref(false)
const submittingReopen = ref(false)
const savingDefinition = ref(false)
const savingCause = ref(false)
const savingPrevention = ref(false)
const uploadingAttachment = ref(false)
const activeTab = ref('overview')
const detailMainRef = ref()
const sideRailHeight = ref('')
const editDialogVisible = ref(false)
const editMode = ref('definition')
const detail = ref(null)
const attachments = ref([])
const attachmentInputRef = ref()
const closeDialogVisible = ref(false)
const reopenDialogVisible = ref(false)
const closeFormRef = ref()
const reopenFormRef = ref()
const ruleOptions = ref({})
const priorityOptions = computed(() => ruleOptions.value.priorities || ['高', '中', '低'])
const attributionOptions = computed(() => ruleOptions.value.causeCategories || ['原因待确认'])
const problemCategoryOptions = computed(() => ruleOptions.value.problemCategories || ['待确认问题'])
const sourceOptions = computed(() => ruleOptions.value.sourceChannels || ['客户微信群', '电话', '邮件', '现场巡检', '系统告警', 'Excel/CSV 导入', '内部排查', '手动录入'])
const systemTypeOptions = computed(() => ruleOptions.value.systemTypes || ['未确认'])
const sideRailStyle = computed(() => (sideRailHeight.value ? { '--side-rail-height': sideRailHeight.value } : {}))

const definitionForm = reactive({
  reporterName: '',
  categoryPath: '',
  source: '',
  buildingName: '',
  floorName: '',
  areaName: '',
  systemType: '',
  devicePoint: '',
  receivedAt: '',
  createTime: '',
  itemTitle: '',
  description: '',
  impactScope: '',
  priority: ''
})
const causeForm = reactive({ causeCategory: '', causeDetail: '', internalConclusion: '', customerFeedback: '' })
const preventionForm = reactive({ notes: '', preventiveAction: '', followUpAction: '', reuseTags: '', knowledgeIncluded: 1 })
const recordForm = reactive({ content: '' })
const closeForm = reactive({
  content: '',
  causeCategory: '',
  causeDetail: '',
  customerFeedback: '',
  preventiveAction: '',
  followUpAction: '',
  reuseTags: '',
  knowledgeIncluded: 1,
  completedAt: ''
})
const reopenForm = reactive({ reason: '问题重新出现，需要继续跟进。' })
const closeRules = {
  content: [{ required: true, message: '请输入处理结论', trigger: 'blur' }],
  causeCategory: [{ required: true, message: '请选择归因分类', trigger: 'change' }]
}
const reopenRules = {
  reason: [{ required: true, message: '请输入重开原因', trigger: 'blur' }]
}

const imageAttachments = computed(() => attachments.value.filter(attachment => String(attachment.fileType || '').startsWith('image/')))
const imagePreviewUrls = computed(() => imageAttachments.value.map(attachment => attachment.previewUrl))
const processRecords = computed(() => [...(detail.value?.records || [])].reverse())
const timelineRecords = computed(() => processRecords.value.slice(0, 5))
const priorityText = computed(() => detail.value?.priority || '中')
const locationText = computed(() => [detail.value?.buildingName, detail.value?.floorName, detail.value?.areaName].filter(Boolean).join(' / ') || '-')
const reuseTagList = computed(() => String(detail.value?.reuseTags || '').split(/[,，]/).map(item => item.trim()).filter(Boolean))
const editDialogTitle = computed(() => ({
  definition: '编辑问题概览',
  cause: '编辑原因与结论',
  prevention: '编辑预防与知识沉淀'
}[editMode.value] || '编辑信息')
)
const savingCurrentEdit = computed(() => (
  editMode.value === 'definition' ? savingDefinition.value : editMode.value === 'cause' ? savingCause.value : savingPrevention.value
))
const overviewPairs = computed(() => [
  { label: '来源渠道', value: valueText(detail.value?.source) },
  { label: '问题大类', value: valueText(detail.value?.categoryPath) },
  { label: '紧急程度', value: valueText(detail.value?.priority) },
  { label: '楼层', value: valueText(detail.value?.floorName) },
  { label: '区域/房间', value: valueText(detail.value?.areaName) },
  { label: '系统/设备', value: valueText(detail.value?.systemType) },
  { label: '设备编号/点位', value: valueText(detail.value?.devicePoint) },
  { label: '处理人', value: valueText(detail.value?.ownerName) }
])
const completionStatus = computed(() => {
  const itemTitle = Boolean(detail.value?.itemTitle)
  const basicDone = Boolean(detail.value?.projectId && detail.value?.receivedAt && itemTitle)
  const descriptionDone = Boolean(detail.value?.description || detail.value?.itemTitle)
  const causeDone = Boolean(detail.value?.causeCategory || detail.value?.causeDetail)
  const recordDone = (detail.value?.records || []).length > 0
  const preventionDone = Boolean(detail.value?.internalConclusion || detail.value?.latestProgress || detail.value?.preventiveAction || detail.value?.followUpAction)
  const checks = [
    ['基础信息', basicDone],
    ['问题现象', descriptionDone],
    ['原因归因', causeDone],
    ['处理记录', recordDone],
    ['预防沉淀', preventionDone]
  ]
  const done = checks.filter(([, value]) => value).map(([label]) => label)
  const todo = checks.filter(([, value]) => !value).map(([label]) => label)
  const score = done.length * 20
  return {
    score,
    done,
    todo,
    needsClosedWarning: detail.value?.currentStatus === 'CLOSED' && !preventionDone
  }
})

async function loadDetail(issueId = route.params.id) {
  loading.value = true
  try {
    const res = await getIssueDetail(issueId)
    detail.value = res.data
    syncForms()
  } finally {
    loading.value = false
  }
}

async function loadAttachments(issueId = route.params.id) {
  const res = await getIssueAttachments(issueId)
  attachments.value = res.data || []
}

async function loadRuleOptions() {
  const res = await getRuleOptions()
  ruleOptions.value = res.data || {}
}

function syncForms() {
  Object.assign(definitionForm, {
    reporterName: detail.value?.reporterName || '',
    categoryPath: detail.value?.categoryPath || '',
    source: detail.value?.source || '',
    buildingName: detail.value?.buildingName || '',
    floorName: detail.value?.floorName || '',
    areaName: detail.value?.areaName || '',
    systemType: detail.value?.systemType || '',
    devicePoint: detail.value?.devicePoint || '',
    receivedAt: toDateTimeModel(detail.value?.receivedAt),
    createTime: toDateTimeModel(detail.value?.createTime || detail.value?.receivedAt),
    itemTitle: detail.value?.itemTitle || '',
    description: detail.value?.description || detail.value?.itemTitle || '',
    impactScope: detail.value?.impactScope || '',
    priority: detail.value?.priority || ''
  })
  Object.assign(causeForm, {
    causeCategory: detail.value?.causeCategory || '',
    causeDetail: detail.value?.causeDetail || '',
    internalConclusion: detail.value?.internalConclusion || detail.value?.latestProgress || '',
    customerFeedback: detail.value?.customerFeedback || ''
  })
  Object.assign(preventionForm, {
    notes: detail.value?.notes || '',
    preventiveAction: detail.value?.preventiveAction || '',
    followUpAction: detail.value?.followUpAction || '',
    reuseTags: detail.value?.reuseTags || '',
    knowledgeIncluded: detail.value?.knowledgeIncluded ?? 1
  })
}

function recordActionLabel(actionType) {
  return actionTypeMap[actionType] || actionType || '处理动作'
}

function openEditDialog(mode) {
  syncForms()
  editMode.value = mode
  editDialogVisible.value = true
}

async function submitEditDialog() {
  if (editMode.value === 'definition') {
    await saveDefinition()
  } else if (editMode.value === 'cause') {
    await saveCause()
  } else {
    await savePrevention()
  }
  editDialogVisible.value = false
}

async function saveDefinition() {
  savingDefinition.value = true
  try {
    await updateIssue(route.params.id, { ...definitionForm })
    ElMessage.success('问题概览已更新')
    await loadDetail()
  } finally {
    savingDefinition.value = false
  }
}

async function saveCause() {
  savingCause.value = true
  try {
    await updateIssue(route.params.id, { ...causeForm })
    ElMessage.success('原因与结论已更新')
    await loadDetail()
  } finally {
    savingCause.value = false
  }
}

async function savePrevention() {
  savingPrevention.value = true
  try {
    await updateIssue(route.params.id, { ...preventionForm })
    ElMessage.success('预防沉淀已更新')
    await loadDetail()
  } finally {
    savingPrevention.value = false
  }
}

function triggerAttachmentSelect() {
  attachmentInputRef.value?.click()
}

async function handleAttachmentChange(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  uploadingAttachment.value = true
  try {
    const compressedFiles = []
    for (const file of files) {
      compressedFiles.push(await compressImageFile(file))
    }
    const results = await Promise.allSettled(compressedFiles.map(file => uploadIssueAttachment(route.params.id, file)))
    if (results.some(result => result.status === 'rejected')) {
      ElMessage.warning('部分图片上传失败，可稍后重试')
    } else {
      ElMessage.success('图片已上传')
    }
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error.message || '上传失败，请稍后重试或检查文件格式')
  } finally {
    uploadingAttachment.value = false
  }
}

function compressImageFile(file) {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    return Promise.reject(new Error('仅支持 jpg、jpeg、png、webp 图片'))
  }

  return new Promise((resolve, reject) => {
    const image = new Image()
    const objectUrl = URL.createObjectURL(file)
    image.onload = () => {
      const scale = image.width > 1920 ? 1920 / image.width : 1
      const width = Math.round(image.width * scale)
      const height = Math.round(image.height * scale)
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')
      context.drawImage(image, 0, 0, width, height)
      const outputType = file.type === 'image/png' ? 'image/png' : file.type === 'image/webp' ? 'image/webp' : 'image/jpeg'
      canvas.toBlob(blob => {
        URL.revokeObjectURL(objectUrl)
        if (!blob) {
          reject(new Error('上传失败，请稍后重试或检查文件格式'))
          return
        }
        if (blob.size > 10 * 1024 * 1024) {
          reject(new Error('文件超过 10MB，请压缩后再上传'))
          return
        }
        const extension = outputType === 'image/png' ? 'png' : outputType === 'image/webp' ? 'webp' : 'jpg'
        const baseName = file.name.replace(/\.[^.]+$/, '')
        resolve(new File([blob], `${baseName}.${extension}`, { type: outputType }))
      }, outputType, 0.75)
    }
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('上传失败，请稍后重试或检查文件格式'))
    }
    image.src = objectUrl
  })
}

function openAttachment(attachment) {
  if (attachment?.previewUrl) {
    window.open(attachment.previewUrl, '_blank')
  }
}

async function removeAttachment(attachment) {
  try {
    await ElMessageBox.confirm(`确认删除附件“${attachment.fileName}”？`, '删除附件', { type: 'warning' })
    await deleteIssueAttachment(route.params.id, attachment.id)
    ElMessage.success('附件已删除')
    await loadAttachments()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '无权删除该附件')
  }
}

async function submitRecord() {
  if (!recordForm.content.trim()) return
  submittingRecord.value = true
  try {
    await addIssueRecord(route.params.id, { content: recordForm.content })
    recordForm.content = ''
    ElMessage.success('处理记录已追加')
    await loadDetail()
  } finally {
    submittingRecord.value = false
  }
}

function openCloseDialog() {
  if (!detail.value?.ownerName || detail.value.ownerName === '未分配') {
    ElMessage.warning('请先在问题概览中填写处理人，再标记闭环')
    openEditDialog('definition')
    return
  }
  Object.assign(closeForm, {
    content: detail.value?.internalConclusion || detail.value?.latestProgress || '',
    causeCategory: detail.value?.causeCategory || '',
    causeDetail: detail.value?.causeDetail || '',
    customerFeedback: detail.value?.customerFeedback || '',
    preventiveAction: detail.value?.preventiveAction || '',
    followUpAction: detail.value?.followUpAction || '',
    reuseTags: detail.value?.reuseTags || '',
    knowledgeIncluded: detail.value?.knowledgeIncluded ?? 1,
    completedAt: ''
  })
  closeDialogVisible.value = true
}

async function submitClose() {
  const valid = await closeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingClose.value = true
  try {
    await closeIssue(route.params.id, closeForm)
    ElMessage.success('问题已闭环')
    closeDialogVisible.value = false
    await loadDetail()
  } finally {
    submittingClose.value = false
  }
}

function openReopenDialog() {
  reopenForm.reason = '问题重新出现，需要继续跟进。'
  reopenDialogVisible.value = true
}

async function submitReopen() {
  const valid = await reopenFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingReopen.value = true
  try {
    await reopenIssue(route.params.id, reopenForm)
    ElMessage.success('问题已重新打开')
    reopenDialogVisible.value = false
    await loadDetail()
  } finally {
    submittingReopen.value = false
  }
}

function goSimilarIssue(row) {
  if (!row?.id || String(row.id) === String(route.params.id)) return
  router.push(`/issues/${row.id}`)
}

function displayProjectName(projectName) {
  if (!projectName) return '-'
  return String(projectName).replace('上海交通大学', '上海交大')
}

function toDateTimeModel(value) {
  if (!value) return ''
  return String(value).replace(' ', 'T').slice(0, 19)
}

function valueText(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function hasText(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function splitDisplayLines(value) {
  if (!hasText(value)) return []
  return String(value)
    .split(/\n|；|;/)
    .map(item => item.replace(/^\s*\d+[.、)]\s*/, '').trim())
    .filter(Boolean)
}

function isImageAttachment(attachment) {
  return String(attachment?.fileType || '').startsWith('image/')
}

function fileTypeLabel(attachment) {
  if (!attachment?.fileType) return '文件'
  const [, subtype] = String(attachment.fileType).split('/')
  return subtype ? subtype.toUpperCase() : String(attachment.fileType).toUpperCase()
}

function formatFileSize(size) {
  const bytes = Number(size)
  if (!Number.isFinite(bytes) || bytes <= 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function goBack() {
  const backPath = window.history.state?.back
  if (backPath && !String(backPath).startsWith('/login') && backPath !== route.fullPath) {
    router.back()
    return
  }
  router.push('/issues')
}

let detailMainResizeObserver

function syncSideRailHeight() {
  if (typeof window === 'undefined') return
  if (window.innerWidth <= 1280) {
    sideRailHeight.value = ''
    return
  }
  window.requestAnimationFrame(() => {
    const height = detailMainRef.value?.getBoundingClientRect?.().height || 0
    sideRailHeight.value = height ? `${Math.ceil(height)}px` : ''
  })
}

function bindDetailMainResizeObserver() {
  detailMainResizeObserver?.disconnect?.()
  if (typeof window !== 'undefined' && detailMainRef.value && 'ResizeObserver' in window) {
    detailMainResizeObserver = new ResizeObserver(() => syncSideRailHeight())
    detailMainResizeObserver.observe(detailMainRef.value)
  }
  syncSideRailHeight()
}

onMounted(async () => {
  await Promise.all([loadRuleOptions(), loadDetail(), loadAttachments()]).catch(() => {})
  await nextTick()
  bindDetailMainResizeObserver()
  window.addEventListener('resize', syncSideRailHeight)
})

watch(
  () => route.params.id,
  async (id, oldId) => {
    if (!id || id === oldId) return
    recordForm.content = ''
    activeTab.value = 'overview'
    detail.value = null
    attachments.value = []
    window.scrollTo({ top: 0, behavior: 'smooth' })
    await Promise.all([loadDetail(id), loadAttachments(id)]).catch(() => {})
    await nextTick()
    bindDetailMainResizeObserver()
  }
)

watch(activeTab, async () => {
  await nextTick()
  syncSideRailHeight()
})

onBeforeUnmount(() => {
  detailMainResizeObserver?.disconnect?.()
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncSideRailHeight)
  }
})
</script>

<style scoped>
.issue-detail-page {
  gap: 14px;
}

.issue-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  align-items: center;
}

.summary-main {
  display: flex;
  gap: 16px;
  min-width: 0;
}

.issue-badge {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #fff0f0;
  color: #e5484d;
  font-weight: 800;
}

.summary-copy {
  min-width: 0;
}

.crumb {
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 10px;
}

.summary-title-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.summary-title-row h1 {
  margin: 0;
  font-size: 21px;
  line-height: 1.35;
  word-break: break-word;
}

.issue-no {
  flex: none;
  padding: 7px 10px;
  border-radius: 8px;
  background: var(--bg-soft);
  color: #263957;
  font-weight: 800;
}

.summary-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 14px;
  color: var(--text-muted);
  font-size: 13px;
}

.summary-actions {
  display: grid;
  justify-items: end;
  gap: 14px;
}

.status-stack {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.completion-strip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  padding: 14px 18px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: #fff;
  box-shadow: var(--shadow-soft);
}

.completion-strip.warning {
  border-color: #f3c969;
  background: #fffaf0;
}

.completion-score {
  display: grid;
  gap: 2px;
  padding-right: 18px;
  border-right: 1px solid var(--line-soft);
}

.completion-score strong {
  color: var(--primary);
  font-size: 24px;
  line-height: 1;
}

.completion-score span,
.completion-copy {
  color: var(--text-muted);
  font-size: 13px;
}

.completion-copy {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.completion-strip :deep(.el-alert) {
  grid-column: 1 / -1;
}

.detail-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.detail-main {
  min-width: 0;
  padding-top: 8px;
}

.side-rail {
  position: sticky;
  top: 88px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: var(--side-rail-height, auto);
  min-height: 0;
  max-height: var(--side-rail-height, calc(100vh - 104px));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
  scrollbar-gutter: stable;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.detail-tabs :deep(.el-tabs__item) {
  font-weight: 700;
}

.tab-page {
  min-height: 0;
}

.tab-head,
.side-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.marked {
  padding-left: 10px;
  border-left: 4px solid var(--primary);
}

.content-block {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fbfdff;
}

.content-block + .content-block,
.attachment-strip,
.info-grid {
  margin-top: 14px;
}

.block-title {
  margin-bottom: 10px;
  font-weight: 800;
  color: #23395d;
}

.basic-info-title {
  margin-top: 16px;
}

.readable-text {
  margin: 0;
  color: #253653;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.attachment-strip {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
}

.attachment-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  gap: 12px;
}

.attachment-image-item {
  min-width: 0;
}

.attachment-image-item :deep(.el-image) {
  width: 100%;
  aspect-ratio: 1.08;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  overflow: hidden;
}

.attachment-name {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.process-tab-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.78fr);
  gap: 16px;
  align-items: start;
}

.record-compose,
.history-panel {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: var(--bg-soft);
}

.history-panel {
  background: #fff;
}

.record-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.process-list.compact {
  margin-top: 12px;
  max-height: 420px;
  overflow: auto;
}

.process-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--line-soft);
}

.process-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.process-title span {
  color: var(--text-muted);
  font-size: 13px;
}

.process-body {
  min-width: 0;
}

.process-body p,
.timeline p {
  margin: 8px 0 0;
  color: #34445f;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.process-list.compact .process-item {
  padding: 12px 0;
}

.process-list.compact .process-body p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.two-column-blocks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.wide-block {
  grid-column: 1 / -1;
}

.side-card {
  padding: 18px;
  box-shadow: var(--shadow-soft);
}

.side-fold-card {
  display: block;
}

.side-fold-card > summary {
  list-style: none;
}

.side-fold-card > summary::-webkit-details-marker {
  display: none;
}

.side-fold-card > .side-card-head {
  align-items: center;
  margin-bottom: 0;
  cursor: pointer;
  user-select: none;
}

.side-fold-card[open] > .side-card-head {
  margin-bottom: 12px;
}

.side-fold-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.fold-icon {
  color: var(--text-muted);
  transition: transform 0.18s ease;
}

.side-fold-card[open] .fold-icon {
  transform: rotate(180deg);
}

.side-fold-body {
  display: grid;
  gap: 12px;
}

.side-upload-row {
  display: flex;
  justify-content: flex-end;
}

.timeline {
  margin-top: 10px;
}

.similar-list {
  display: grid;
  gap: 10px;
}

.similar-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  color: #34445f;
  text-align: left;
  cursor: pointer;
}

.similar-item:hover {
  border-color: var(--primary);
  background: #f7fbff;
}

.similar-item strong {
  color: #2f405b;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
}

.similar-item small {
  color: #7d8aa0;
  font-size: 12px;
  font-weight: 400;
}

.attachment-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.summary-count {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--bg-soft);
}

.summary-count strong {
  font-size: 22px;
  color: var(--primary);
}

.summary-count span {
  color: var(--text-muted);
  font-size: 12px;
}

.file-list {
  display: grid;
  gap: 8px;
}

.file-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  color: var(--text-muted);
  font-size: 13px;
}

.file-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-row button {
  border: none;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
}

.overview-stack,
.process-left,
.prevention-layout,
.cause-layout {
  display: grid;
  gap: 14px;
}

.overview-section,
.insight-card,
.process-suggestion-card {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.section-line-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}

.section-line-head.compact {
  align-items: center;
  margin-bottom: 0;
}

.section-line-head .block-title {
  margin-bottom: 2px;
}

.section-icon {
  flex: none;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  font-size: 15px;
}

.section-icon.blue {
  color: #1677ff;
  background: #eaf3ff;
}

.section-icon.green {
  color: #16a05d;
  background: #eaf8f1;
}

.section-icon.violet {
  color: #7357f6;
  background: #f0edff;
}

.section-icon.cyan {
  color: #0f7ccf;
  background: #e9f7ff;
}

.mini-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.lead-text {
  font-size: 14px;
}

.overview-file-list {
  display: grid;
  gap: 10px;
}

.overview-file-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border: 1px solid #edf2fa;
  border-radius: var(--radius-md);
  background: #fff;
}

.overview-file-thumb,
.side-file-thumb {
  width: 96px;
  height: 62px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  overflow: hidden;
  background: var(--bg-soft);
}

.file-thumb-fallback {
  display: grid;
  place-items: center;
  color: var(--primary);
  font-size: 22px;
}

.overview-file-meta,
.side-file-copy,
.record-person {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.overview-file-meta strong,
.side-file-copy strong {
  overflow: hidden;
  color: #173154;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-file-meta span,
.overview-file-meta small,
.side-file-copy span,
.record-person span,
.record-card-head time {
  color: var(--text-muted);
  font-size: 12px;
}

.info-grid .info-pair {
  padding: 12px 14px;
  border: 1px solid #edf2fa;
  border-radius: 8px;
  background: #f7faff;
}

.process-tab-grid {
  grid-template-columns: minmax(0, 0.95fr) minmax(320px, 1fr);
}

.record-compose,
.history-panel {
  background: #fff;
}

.record-compose :deep(.el-textarea__inner) {
  min-height: 176px;
  line-height: 1.7;
}

.record-actions {
  flex-wrap: wrap;
}

.record-tips {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #d8eaff;
  border-radius: var(--radius-md);
  background: #f6fbff;
}

.tips-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--primary);
  font-weight: 800;
}

.record-tips ul,
.suggestion-grid ul,
.suggestion-note ul,
.action-list,
.reason-list {
  margin: 0;
  padding-left: 18px;
  color: #334765;
  line-height: 1.8;
}

.process-suggestion-card {
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.side-card-head.slim {
  align-items: center;
  margin-bottom: 12px;
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.suggestion-grid > div {
  padding: 12px;
  border-radius: 8px;
  background: #f7faff;
}

.suggestion-grid strong {
  display: block;
  margin-bottom: 6px;
  color: #173154;
}

.suggestion-grid p {
  margin: 0;
  color: #34445f;
  line-height: 1.7;
}

.history-record-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
}

.history-record-card + .history-record-card {
  margin-top: 12px;
}

.record-card-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.record-person strong {
  color: #173154;
}

.history-record-card .readable-text {
  color: #34445f;
  font-size: 13px;
}

.cause-layout,
.prevention-layout {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.insight-card {
  min-height: 164px;
}

.reason-category-card {
  display: grid;
  align-content: start;
  gap: 12px;
}

.reason-detail-card {
  min-height: 260px;
}

.reason-list,
.action-list {
  padding-left: 22px;
}

.suggestion-note,
.expected-result,
.knowledge-state {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid #cdebd9;
  border-radius: 8px;
  background: #f1fbf5;
  color: #276846;
}

.suggestion-note strong {
  display: block;
  margin-bottom: 6px;
}

.expected-result,
.knowledge-state {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  line-height: 1.7;
}

.knowledge-state p {
  margin: 4px 0 0;
  color: #5d806a;
}

.knowledge-state.muted {
  border-color: var(--line-soft);
  background: var(--bg-soft);
  color: var(--text-muted);
}

.knowledge-state.muted p {
  color: var(--text-muted);
}

.pill-tags {
  gap: 8px;
}

.pill-tags :deep(.el-tag) {
  border-color: #b9d7ff;
  background: #eef6ff;
}

.note-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.side-card {
  background: #fff;
}

.side-timeline {
  position: relative;
  display: grid;
  gap: 16px;
  margin-top: 8px;
  padding-left: 18px;
  max-height: min(45vh, 360px);
  overflow-y: auto;
  padding-right: 4px;
}

.side-timeline::before {
  content: "";
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 5px;
  width: 1px;
  background: #cfe0f4;
}

.side-timeline-item {
  position: relative;
  display: grid;
  gap: 4px;
}

.timeline-dot {
  position: absolute;
  top: 4px;
  left: -18px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #4cb64f;
  box-shadow: 0 0 0 4px #eef9ef;
}

.timeline-dot.success {
  background: var(--success);
}

.side-timeline-item strong {
  color: #173154;
}

.side-timeline-item p {
  margin: 4px 0;
  color: #34445f;
  line-height: 1.65;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
}

.side-timeline-item time {
  color: var(--text-muted);
  font-size: 12px;
}

.similar-item {
  border-color: #e5edf8;
  box-shadow: 0 1px 4px rgba(16, 38, 76, 0.04);
}

.similar-item .mono {
  color: #2f78d8;
  font-size: 12px;
  font-weight: 650;
}

.file-row.rich {
  grid-template-columns: 44px minmax(0, 1fr) auto auto;
  padding: 8px;
  border: 1px solid #edf2fa;
  border-radius: 8px;
  background: #fff;
}

.file-row.rich .side-file-thumb {
  width: 44px;
  height: 36px;
}

.file-row.rich button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
}

.hidden-file-input {
  display: none;
}

.section-form {
  margin-top: 6px;
}

.definition-grid,
.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.close-form :deep(.el-form-item),
.section-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

@media (max-width: 1280px) {
  .detail-workspace,
  .issue-summary {
    grid-template-columns: 1fr;
  }

  .summary-actions {
    justify-items: start;
  }

  .side-rail {
    position: static;
    height: auto;
    max-height: none;
    overflow: visible;
    overscroll-behavior: auto;
    padding-right: 0;
    scrollbar-gutter: auto;
  }
}

@media (max-width: 860px) {
  .info-grid,
  .two-column-blocks,
  .cause-layout,
  .prevention-layout,
  .process-tab-grid,
  .suggestion-grid,
  .definition-grid,
  .dialog-grid {
    grid-template-columns: 1fr;
  }

  .overview-file-row {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .overview-file-row .el-button {
    grid-column: 2;
    justify-self: start;
  }

  .overview-file-thumb {
    width: 76px;
    height: 58px;
  }

  .file-row.rich {
    grid-template-columns: 44px minmax(0, 1fr) auto;
  }

  .completion-strip {
    grid-template-columns: 1fr;
  }

  .completion-score {
    border-right: none;
    border-bottom: 1px solid var(--line-soft);
    padding: 0 0 12px;
  }
}
</style>
