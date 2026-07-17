<template>
  <div class="page-shell maintenance-page" v-loading="loading">
    <section class="section-card visit-hero">
      <div class="visit-title-block">
        <div class="breadcrumb-line">运维管理 / 当前批次</div>
        <h1>{{ selectedVisit?.visitTitle || '请选择运维批次' }}</h1>
        <p>
          {{ selectedVisit ? `${selectedVisit.visitNo} / ${selectedVisit.projectName} / ${selectedVisit.servicePeriod}` : '从切换批次中选择一个运维任务后开始查看模板内容。' }}
        </p>
      </div>
      <div v-if="selectedVisit" class="visit-hero-stats">
        <div><span>安排</span><strong>{{ visitStats.assignments }}</strong></div>
        <div><span>记录</span><strong>{{ visitStats.findings }}</strong></div>
        <div><span>待处理</span><strong class="warning-text">{{ visitStats.unresolved }}</strong></div>
        <div><span>报价</span><strong>{{ moneyText(quoteTotal) }}</strong></div>
      </div>
      <div class="toolbar-actions">
        <el-button @click="batchDrawerVisible = true">切换批次</el-button>
        <el-button type="primary" :disabled="isTemporary" @click="openVisitCreate">
          <el-icon><Plus /></el-icon>
          新增运维
        </el-button>
        <el-button :loading="exporting" :disabled="!selectedVisit || isTemporary" @click="exportReport">
          <el-icon><Download /></el-icon>
          导出报告
        </el-button>
      </div>
    </section>

    <section v-if="selectedVisit" class="section-card detail-card">
      <div class="template-status-row">
        <el-tag :type="visitStatusMeta(selectedVisit.status).type">{{ visitStatusMeta(selectedVisit.status).label }}</el-tag>
        <el-button :disabled="isTemporary || selectedVisit.status !== 'PLANNED'" @click="startVisit">开始运维</el-button>
        <el-button type="success" :disabled="isTemporary || selectedVisit.status === 'CLOSED'" @click="openCloseDialog">关闭并沉淀知识</el-button>
        <el-button :disabled="isTemporary" @click="openVisitEdit(selectedVisit)">编辑批次</el-button>
      </div>
      <el-tabs v-model="activeTab" class="ops-tabs">
        <el-tab-pane label="总览" name="overview">
          <div class="overview-layout">
            <div class="overview-main">
              <div class="ops-kpi-grid">
                <article v-for="item in visitKpis" :key="item.label" class="ops-kpi-card">
                  <span>{{ item.label }}</span>
                  <strong :class="item.className">{{ item.value }}</strong>
                  <em>{{ item.note }}</em>
                </article>
              </div>

              <section class="ops-panel">
                <div class="panel-head">
                  <div>
                    <div class="block-title">流程进度</div>
                    <p class="section-hint">来自历史模板中的分工安排、检查清单、详细记录、报价和报告结构。</p>
                  </div>
                  <el-progress :percentage="visitProgress" :stroke-width="10" class="progress-inline" />
                </div>
                <div class="step-grid">
                  <button v-for="step in tabSteps" :key="step.tab" type="button" class="step-card" @click="goTab(step.tab)">
                    <el-icon><component :is="step.icon" /></el-icon>
                    <strong>{{ step.title }}</strong>
                    <span>{{ step.countText }}</span>
                    <el-tag size="small" :type="step.done ? 'success' : 'info'" effect="plain">{{ step.done ? '已形成' : '待完善' }}</el-tag>
                  </button>
                </div>
              </section>

              <section class="ops-panel">
                <div class="panel-head">
                  <div>
                    <div class="block-title">本次重点问题</div>
                    <p class="section-hint">优先展示待整改、需报价和已沉淀知识库的现场记录。</p>
                  </div>
                  <el-button link type="primary" @click="goTab('findings')">查看全部</el-button>
                </div>
                <div class="issue-focus-list" v-if="focusFindings.length">
                  <article v-for="item in focusFindings" :key="item.id" class="issue-focus-card" @click="selectFinding(item)">
                    <div>
                      <strong>{{ item.issueDescription || '未填写问题描述' }}</strong>
                      <span>{{ [item.floorName, item.areaName].filter(Boolean).join(' / ') || '未标注位置' }}</span>
                    </div>
                    <div class="inline-tags">
                      <el-tag size="small" :type="findingStatusType(item.completionStatus)" effect="plain">{{ item.completionStatus || '待处理' }}</el-tag>
                      <el-tag v-if="Number(item.quoteRequired) === 1" size="small" type="warning" effect="plain">需报价</el-tag>
                      <el-tag v-if="Number(item.knowledgeIncluded) === 1" size="small" type="success" effect="plain">OPERATIONS</el-tag>
                    </div>
                  </article>
                </div>
                <el-empty v-else description="暂无重点问题" :image-size="86" />
              </section>
            </div>

            <aside class="overview-side">
              <section class="ops-panel">
                <div class="block-title">项目概览</div>
                <div class="side-info-list">
                  <div><span>客户</span><strong>{{ selectedVisit.customerName || '-' }}</strong></div>
                  <div><span>项目</span><strong>{{ selectedVisit.projectName || '-' }}</strong></div>
                  <div><span>周期</span><strong>{{ selectedVisit.servicePeriod || '-' }}</strong></div>
                  <div><span>计划开始</span><strong>{{ formatDateTime(selectedVisit.plannedStartAt) }}</strong></div>
                  <div><span>实际结束</span><strong>{{ formatDateTime(selectedVisit.actualEndAt) }}</strong></div>
                </div>
              </section>
              <section class="ops-panel">
                <div class="block-title">最近动态</div>
                <ol class="activity-list">
                  <li v-for="item in recentActivities" :key="item.key">
                    <span>{{ item.time }}</span>
                    <strong>{{ item.text }}</strong>
                  </li>
                </ol>
              </section>
              <section class="ops-panel">
                <div class="block-title">来源文件</div>
                <div class="source-mini-list">
                  <div v-for="file in sourceSummary" :key="file.fileType">
                    <span>{{ file.fileType }}</span>
                    <strong>{{ file.count }}</strong>
                  </div>
                </div>
              </section>
            </aside>
          </div>
        </el-tab-pane>

        <el-tab-pane label="运维前安排" name="assignments">
          <div class="tab-toolbar">
            <span>{{ selectedVisit.assignments?.length || 0 }} 条安排 / {{ selectedVisit.personnel?.length || 0 }} 名人员</span>
            <div class="toolbar-actions">
              <el-button size="small" @click="personnelReportDialogVisible = true">
                <el-icon><User /></el-icon>
                人员报备
              </el-button>
              <el-button size="small" type="primary" :disabled="isTemporary" @click="openAssignmentCreate">新增安排</el-button>
            </div>
          </div>
          <div class="template-stack">
            <section class="ops-panel template-table-panel">
              <div class="panel-head">
                <div>
                  <div class="block-title">运维前事项安排表</div>
                  <p class="section-hint">贴合历史资料“手工安排 / 分工安排”Sheet：日期、时间、楼层、事项、现场人员、负责人。带 * 的时间为原表缺失时间时按路线顺序生成的展示时间。</p>
                </div>
              </div>
              <el-table :data="selectedVisit.assignments || []" empty-text="暂无运维前安排" max-height="420" border>
                <el-table-column label="日期" width="122">
                  <template #default="{ row }">{{ dateText(row.scheduledAt) }}</template>
                </el-table-column>
                <el-table-column label="时间" width="96">
                  <template #default="{ row, $index }">{{ assignmentDisplayTime(row, $index) }}</template>
                </el-table-column>
                <el-table-column prop="floorName" label="楼层" width="100" />
                <el-table-column prop="taskItem" label="事项" min-width="260" show-overflow-tooltip />
                <el-table-column prop="ownerName" label="现场人员" width="150" />
                <el-table-column prop="notes" label="负责人/备注" min-width="160" show-overflow-tooltip />
                <el-table-column label="状态" width="110">
                  <template #default="{ row }"><el-tag size="small" effect="plain">{{ assignmentStatusText(row.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button link type="primary" :disabled="isTemporary" @click="openAssignmentEdit(row)">编辑</el-button>
                    <el-button link type="danger" :disabled="isTemporary" @click="removeAssignment(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>

          </div>

          <section class="ops-panel schedule-panel">
            <div class="panel-head">
              <div>
                <div class="block-title">巡检路线日程视图</div>
                <p class="section-hint">按楼层巡检路线和时间展开排期。</p>
              </div>
              <el-date-picker v-model="scheduleDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" />
            </div>
            <div class="schedule-board" v-if="scheduleItems.length">
              <div class="schedule-grid">
                <div class="schedule-corner">巡检路线</div>
                <div class="schedule-hours">
                  <span v-for="hour in scheduleHours" :key="hour">{{ hour }}:00</span>
                </div>
                <div class="schedule-route-label">楼层巡检路线</div>
                <div class="schedule-hour-cells">
                  <div v-for="group in scheduleHourGroups" :key="group.hour" class="schedule-hour-cell">
                    <button
                      v-if="group.items.length"
                      type="button"
                      class="schedule-route-item"
                      :class="group.tone"
                      :title="group.tooltip"
                      @click="openAssignmentEdit(group.raw)"
                    >
                      <span>{{ group.timeText }}</span>
                      <strong>{{ group.floorText }}</strong>
                      <em>{{ group.title }}</em>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无可视化排期" :image-size="88" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="检查清单" name="checklist">
          <div class="checklist-page">
            <section class="ops-panel checklist-matrix-panel">
              <div class="checklist-section-head">
                <div>
                  <span class="section-index">01</span>
                  <div>
                    <div class="block-title">检查清单视图</div>
                    <p class="section-hint">按检查项与楼层交叉呈现，异常标签来源于“检查详细记录”。</p>
                  </div>
                </div>
                <div class="checklist-head-meta">
                  <div><span>检查日期</span><strong>{{ checklistInspectionDate }}</strong></div>
                  <div><span>覆盖楼层</span><strong>{{ checklistFloors.length }}</strong></div>
                  <div><span>异常记录</span><strong class="danger-text">{{ checklistFindings.length }}</strong></div>
                </div>
              </div>

              <div class="checklist-legend">
                <span><i class="legend-dot normal"></i>正常 / 未发现异常</span>
                <span><i class="legend-dot resolved"></i>发现问题，已解决</span>
                <span><i class="legend-dot open"></i>发现问题，待解决</span>
                <em>点击异常标签查看详细记录</em>
              </div>

              <div class="checklist-matrix-scroll">
                <table class="checklist-matrix">
                  <thead>
                    <tr>
                      <th class="matrix-category-column">分类</th>
                      <th class="matrix-item-column">检查项</th>
                      <th v-for="floor in checklistFloors" :key="floor">{{ floor }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in checklistMatrixRows" :key="row.key">
                      <th v-if="row.categoryRowSpan" :rowspan="row.categoryRowSpan" class="matrix-category-cell">
                        <span>{{ row.categoryIndex }}</span>
                        <strong>{{ row.category }}</strong>
                      </th>
                      <td class="matrix-item-cell">{{ row.label }}</td>
                      <td v-for="cell in row.cells" :key="cell.floor" class="matrix-status-cell">
                        <span v-if="!cell.issues.length" class="matrix-normal-mark" title="未发现异常">&#10003;</span>
                        <button
                          v-else
                          type="button"
                          class="matrix-issue-tag"
                          :class="cell.hasOpen ? 'is-open' : 'is-resolved'"
                          :title="cell.tooltip"
                          @click="selectFinding(cell.issues[0].finding)"
                        >
                          {{ cell.label }}
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section class="ops-panel checklist-exception-panel">
              <div class="checklist-section-head">
                <div>
                  <span class="section-index">02</span>
                  <div>
                    <div class="block-title">异常情况记录</div>
                    <p class="section-hint">本次检查异常按五类自动汇总，状态随详细记录实时更新。</p>
                  </div>
                </div>
                <el-button link type="primary" @click="goTab('findings')">查看全部详细记录</el-button>
              </div>

              <div class="checklist-report-summary">
                <p>
                  本次检查共形成 <strong>{{ checklistSummaryTotals.total }}</strong> 项记录，
                  正常 <strong class="success-text">{{ checklistSummaryTotals.normal }}</strong> 项，
                  异常 <strong class="danger-text">{{ checklistSummaryTotals.abnormal }}</strong> 项；
                  其中已解决 <strong>{{ checklistSummaryTotals.resolved }}</strong> 项，
                  待解决 <strong class="danger-text">{{ checklistSummaryTotals.open }}</strong> 项。
                </p>
                <ol v-if="checklistSummaryTotals.abnormal" class="condensed-category-list">
                  <li v-for="item in checklistCategorySummary.filter(row => row.total)" :key="item.key">
                    <span :class="item.tone"></span>
                    <p><strong>{{ item.label }}：</strong>{{ item.summary }}</p>
                  </li>
                </ol>
                <el-empty v-else description="本次检查未发现异常" :image-size="72" />
              </div>
            </section>

            <section class="ops-panel checklist-solution-panel">
              <div class="checklist-section-head">
                <div>
                  <span class="section-index">03</span>
                  <div>
                    <div class="block-title">解决方案</div>
                    <p class="section-hint">已解决项提炼处理结果，待解决项按问题、影响和方案形成行动清单。</p>
                  </div>
                </div>
              </div>

              <div class="concise-solution-summary">
                <section>
                  <span class="solution-number">1</span>
                  <div>
                    <h4>已解决问题</h4>
                    <p>{{ resolvedChecklistSummary }}</p>
                  </div>
                </section>
                <section>
                  <span class="solution-number pending">2</span>
                  <div>
                    <h4>待解决问题</h4>
                    <ol v-if="pendingChecklistCategorySolutions.length" class="pending-category-summary-list">
                      <li v-for="(item, index) in pendingChecklistCategorySolutions" :key="item.key">
                        <strong>2.{{ index + 1 }} {{ item.label }}</strong>
                        <p><span>问题概述：</span>{{ item.overview }}</p>
                        <p><span>对客户影响：</span>{{ item.impact }}</p>
                        <p><span>解决方案：</span>{{ item.solution }}</p>
                      </li>
                    </ol>
                    <p v-else>本次检查暂无待解决问题。</p>
                  </div>
                </section>
              </div>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="检查详细记录" name="findings">
          <div class="tab-toolbar">
            <span>{{ filteredFindings.length }} / {{ selectedVisit.findings?.length || 0 }} 条现场记录</span>
            <div class="toolbar-actions">
              <el-select v-model="findingStatusFilter" clearable placeholder="全部状态" class="status-filter">
                <el-option v-for="status in findingStatusOptions" :key="status" :label="status" :value="status" />
              </el-select>
              <el-input v-model="findingKeyword" clearable placeholder="搜索楼层、区域、问题、处理情况" class="wide-search" />
              <el-button size="small" type="primary" @click="openFindingCreate">新增记录</el-button>
            </div>
          </div>
          <div class="finding-workspace template-finding-workspace">
            <section class="ops-panel template-table-panel">
              <el-table :data="filteredFindings" empty-text="暂无符合条件的记录" max-height="680" border highlight-current-row @row-click="selectFinding">
                <el-table-column type="index" label="序号" width="72" fixed />
                <el-table-column prop="floorName" label="楼层" width="96" />
                <el-table-column prop="areaName" label="位置 / 区域" min-width="150" show-overflow-tooltip />
                <el-table-column label="问题类型" min-width="120">
                  <template #default="{ row }">{{ inferCheckCategory(row) }}</template>
                </el-table-column>
                <el-table-column prop="issueDescription" label="问题描述" min-width="260" show-overflow-tooltip />
                <el-table-column prop="handlingResult" label="进一步查验 / 处理措施" min-width="260" show-overflow-tooltip />
                <el-table-column label="完成情况" width="128">
                  <template #default="{ row }">
                    <el-tag size="small" :type="findingStatusType(row.completionStatus)" effect="plain">{{ row.completionStatus || '待处理' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="标记" width="132">
                  <template #default="{ row }">
                    <el-tag v-if="Number(row.quoteRequired) === 1" size="small" type="warning">报价</el-tag>
                    <el-tag v-if="Number(row.knowledgeIncluded) === 1" size="small" type="success">知识</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click.stop="selectFinding(row)">详情</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>
            <aside class="finding-detail-panel" v-if="selectedFinding">
              <div class="panel-head">
                <div>
                  <div class="block-title">记录详情</div>
                  <p class="section-hint">{{ [selectedFinding.floorName, selectedFinding.areaName].filter(Boolean).join(' / ') || '未标注位置' }}</p>
                </div>
                <div class="toolbar-actions">
                  <el-button size="small" @click="openFindingEdit(selectedFinding)">编辑</el-button>
                  <el-upload :show-file-list="false" :auto-upload="false" accept=".jpg,.jpeg,.png,.webp" :on-change="file => handleAttachmentChange(selectedFinding, file)">
                    <el-button size="small" type="primary">上传图片</el-button>
                  </el-upload>
                </div>
              </div>
              <div class="detail-section">
                <span>问题描述</span>
                <p>{{ selectedFinding.issueDescription || '-' }}</p>
              </div>
              <div class="detail-section">
                <span>进一步查验 / 处理情况</span>
                <p>{{ selectedFinding.handlingResult || '-' }}</p>
              </div>
              <div class="detail-section">
                <span>原因分析</span>
                <p>{{ selectedFinding.causeAnalysis || '-' }}</p>
              </div>
              <div class="detail-section">
                <span>后续动作 / 整改建议</span>
                <p>{{ selectedFinding.followUpAction || '-' }}</p>
              </div>
              <div class="detail-badges">
                <el-tag :type="findingStatusType(selectedFinding.completionStatus)">{{ selectedFinding.completionStatus || '待处理' }}</el-tag>
                <el-tag :type="Number(selectedFinding.quoteRequired) === 1 ? 'warning' : 'info'" effect="plain">{{ Number(selectedFinding.quoteRequired) === 1 ? '需要报价' : '无需报价' }}</el-tag>
                <el-tag :type="Number(selectedFinding.knowledgeIncluded) === 1 ? 'success' : 'info'" effect="plain">{{ Number(selectedFinding.knowledgeIncluded) === 1 ? '已沉淀 OPERATIONS' : '未沉淀知识库' }}</el-tag>
              </div>
              <div class="attachment-grid" v-if="selectedFinding.attachments?.length">
                <el-image
                  v-for="file in selectedFinding.attachments"
                  :key="file.id"
                  :src="file.fileUrl"
                  :preview-src-list="selectedFinding.attachments.map(item => item.fileUrl)"
                  fit="cover"
                />
              </div>
              <el-empty v-else description="暂无现场图片" :image-size="80" />
              <div class="source-box" v-if="selectedFinding.sourceFilePath">
                <span>来源追踪</span>
                <strong>{{ selectedFinding.sourceFilePath }}</strong>
                <em>{{ [selectedFinding.sourceSheet, selectedFinding.sourceRowNumber ? `第${selectedFinding.sourceRowNumber}行` : ''].filter(Boolean).join(' / ') }}</em>
              </div>
            </aside>
          </div>

          <section class="ops-panel compare-panel">
            <div class="panel-head">
              <div>
                <div class="block-title">运维对比</div>
                <p class="section-hint">第一版基于当前结构化记录生成本批次 vs 上次/上季度的对比视图，后续接入真实跨批次差异。</p>
              </div>
              <el-tag effect="plain">预留年度对比</el-tag>
            </div>
            <div class="compare-grid">
              <article v-for="item in comparisonCards" :key="item.label">
                <span>{{ item.label }}</span>
                <strong :class="item.className">{{ item.value }}</strong>
                <em>{{ item.note }}</em>
              </article>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="备品报价" name="quotes">
          <div class="quote-layout">
            <section class="ops-panel">
              <div class="tab-toolbar">
                <span>来自“备品报价清单 / 报价”Sheet，支持从现场记录生成报价项。</span>
                <div class="toolbar-actions">
                  <el-input v-model="quoteKeyword" clearable placeholder="搜索区域、事项、备注" class="wide-search" />
                  <el-button size="small" type="primary" :disabled="isTemporary" @click="openQuoteCreate">新增报价项</el-button>
                </div>
              </div>
              <el-table :data="filteredQuoteItems" empty-text="暂无报价项" max-height="560" show-summary :summary-method="quoteSummaryMethod">
                <el-table-column prop="areaName" label="区域" min-width="120" fixed />
                <el-table-column prop="itemName" label="事项/设备/灯具名称" min-width="240" show-overflow-tooltip />
                <el-table-column prop="quantity" label="数量" width="90" />
                <el-table-column prop="unitName" label="单位" width="90" />
                <el-table-column label="单价" width="120"><template #default="{ row }">{{ moneyText(row.unitPrice) }}</template></el-table-column>
                <el-table-column label="金额" width="130"><template #default="{ row }">{{ moneyText(row.amount) }}</template></el-table-column>
                <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" :disabled="isTemporary" @click="openQuoteEdit(row)">编辑</el-button>
                    <el-button link type="danger" :disabled="isTemporary" @click="removeQuote(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>
            <aside class="ops-panel quote-summary-panel">
              <div class="block-title">报价汇总</div>
              <div class="quote-total">{{ moneyText(quoteTotal) }}</div>
              <div class="side-info-list">
                <div><span>报价项</span><strong>{{ filteredQuoteItems.length }}</strong></div>
                <div><span>涉及区域</span><strong>{{ quoteAreaCount }}</strong></div>
                <div><span>需报价记录</span><strong>{{ visitStats.quoteRequired }}</strong></div>
                <div><span>报价状态</span><strong>{{ filteredQuoteItems.length ? '已生成清单' : '待整理' }}</strong></div>
              </div>
              <el-button class="full-button" :disabled="isTemporary">备件库入口（预留）</el-button>
            </aside>
          </div>
        </el-tab-pane>

        <el-tab-pane label="运维报告" name="report">
          <div class="report-layout">
            <article class="report-paper">
              <header class="report-cover">
                <span>LONTRI 物联网照明系统</span>
                <h2>{{ selectedVisit.projectName }} 运维巡检报告</h2>
                <p>{{ selectedVisit.servicePeriod }} / {{ selectedVisit.visitNo }}</p>
              </header>
              <section class="report-section">
                <h3>一、基本信息</h3>
                <div class="info-grid">
                  <div class="info-pair"><span>客户</span><strong>{{ selectedVisit.customerName || '-' }}</strong></div>
                  <div class="info-pair"><span>项目</span><strong>{{ selectedVisit.projectName || '-' }}</strong></div>
                  <div class="info-pair"><span>计划时间</span><strong>{{ formatDateTime(selectedVisit.plannedStartAt) }}</strong></div>
                  <div class="info-pair"><span>完成时间</span><strong>{{ formatDateTime(selectedVisit.actualEndAt) }}</strong></div>
                </div>
              </section>
              <section class="report-section">
                <h3>二、运维摘要</h3>
                <p class="readable-text">{{ selectedVisit.summary || reportSummary }}</p>
              </section>
              <section class="report-section">
                <h3>三、检查结果汇总</h3>
                <div class="report-stat-grid">
                  <div><span>现场记录</span><strong>{{ visitStats.findings }}</strong></div>
                  <div><span>异常/待处理</span><strong>{{ visitStats.unresolved }}</strong></div>
                  <div><span>已解决</span><strong>{{ visitStats.resolved }}</strong></div>
                  <div><span>报价金额</span><strong>{{ moneyText(quoteTotal) }}</strong></div>
                </div>
              </section>
              <section class="report-section">
                <h3>四、重点问题与处理</h3>
                <article v-for="item in focusFindings" :key="item.id" class="report-finding">
                  <strong>{{ item.issueDescription }}</strong>
                  <p>{{ item.handlingResult || item.followUpAction || '暂无处理说明' }}</p>
                </article>
                <el-empty v-if="!focusFindings.length" description="暂无重点问题" :image-size="80" />
              </section>
              <section class="report-section">
                <h3>五、运维结论</h3>
                <p class="readable-text">{{ selectedVisit.conclusion || '本次运维资料已结构化记录，详细安排、检查记录和报价清单见对应章节。' }}</p>
              </section>
            </article>
            <aside class="ops-panel export-panel">
              <div class="block-title">导出配置</div>
              <el-checkbox checked disabled>基本信息</el-checkbox>
              <el-checkbox checked disabled>运维前安排</el-checkbox>
              <el-checkbox checked disabled>现场记录</el-checkbox>
              <el-checkbox checked disabled>报价清单</el-checkbox>
              <el-button type="primary" :loading="exporting" :disabled="isTemporary" @click="exportReport">导出 Excel</el-button>
              <div class="source-table-title">来源文件</div>
              <el-table :data="selectedVisit.sourceFiles || []" empty-text="暂无来源文件" max-height="320">
                <el-table-column prop="fileType" label="类型" width="76" />
                <el-table-column prop="fileName" label="文件名" min-width="170" show-overflow-tooltip />
                <el-table-column prop="filePath" label="归档路径" min-width="180" show-overflow-tooltip />
              </el-table>
            </aside>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="batchDrawerVisible" title="切换运维批次" size="76%" destroy-on-close>
      <div class="batch-drawer-body">
        <section class="drawer-metrics">
          <article>
            <span>运维批次</span>
            <strong>{{ numberText(overview.totalVisits) }}</strong>
          </article>
          <article>
            <span>进行中</span>
            <strong>{{ numberText(overview.inProgressVisits) }}</strong>
          </article>
          <article>
            <span>未完成记录</span>
            <strong class="danger-text">{{ numberText(overview.unresolvedFindings) }}</strong>
          </article>
          <article>
            <span>报价合计</span>
            <strong>{{ moneyText(overview.quoteTotalAmount) }}</strong>
          </article>
        </section>

        <section class="drawer-filter">
          <el-form label-position="top" class="filter-grid">
            <el-form-item label="项目">
              <el-select v-model="filters.projectId" clearable filterable placeholder="选择项目">
                <el-option v-for="project in selectableProjects" :key="project.id" :label="project.projectName" :value="project.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.status" clearable placeholder="全部状态">
                <el-option label="计划中" value="PLANNED" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </el-form-item>
            <el-form-item label="年份">
              <el-input-number v-model="filters.year" :min="2020" :max="2099" controls-position="right" />
            </el-form-item>
            <el-form-item label="季度">
              <el-select v-model="filters.quarter" clearable placeholder="全部季度">
                <el-option label="Q1" :value="1" />
                <el-option label="Q2" :value="2" />
                <el-option label="Q3" :value="3" />
                <el-option label="Q4" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item label="计划日期" class="wide-item">
              <el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始" end-placeholder="结束" />
            </el-form-item>
            <el-form-item label="关键词" class="wide-item">
              <el-input v-model="filters.keyword" clearable placeholder="搜索编号、标题、周期、摘要、结论" @keyup.enter="applyFilters" />
            </el-form-item>
            <el-form-item class="action-item">
              <div class="filter-actions">
                <el-button @click="resetFilters">重置</el-button>
                <el-button type="primary" @click="applyFilters">查询</el-button>
              </div>
            </el-form-item>
          </el-form>
        </section>

        <section class="drawer-table">
          <el-table :data="visits" row-key="id" highlight-current-row empty-text="暂无运维批次" max-height="560" @row-click="selectVisit">
            <el-table-column prop="visitNo" label="编号" min-width="155" />
            <el-table-column prop="visitTitle" label="标题" min-width="210" show-overflow-tooltip />
            <el-table-column prop="projectName" label="项目" min-width="160" show-overflow-tooltip />
            <el-table-column prop="servicePeriod" label="周期" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag :type="visitStatusMeta(row.status).type">{{ visitStatusMeta(row.status).label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="计划时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.plannedStartAt) }}</template>
            </el-table-column>
            <el-table-column label="现场记录" width="105">
              <template #default="{ row }">{{ row.unresolvedFindingCount || 0 }} / {{ row.findingCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="报价" width="120">
              <template #default="{ row }">{{ moneyText(row.quoteTotalAmount) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="isTemporary" @click.stop="openVisitEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <span>共 {{ numberText(pagination.total) }} 个批次</span>
            <el-pagination
              background
              layout="sizes, prev, pager, next, jumper"
              :page-sizes="[10, 20, 50]"
              :current-page="pagination.page"
              :page-size="pagination.pageSize"
              :total="pagination.total"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="visitDialogVisible" :title="editingVisit ? '编辑运维批次' : '新增运维批次'" width="760px">
      <el-form :model="visitForm" label-position="top" class="dialog-grid">
        <el-form-item label="项目">
          <el-select v-model="visitForm.projectId" filterable placeholder="选择项目">
            <el-option v-for="project in selectableProjects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="visitForm.visitTitle" />
        </el-form-item>
        <el-form-item label="年份">
          <el-input-number v-model="visitForm.serviceYear" :min="2020" :max="2099" controls-position="right" />
        </el-form-item>
        <el-form-item label="季度">
          <el-select v-model="visitForm.serviceQuarter">
            <el-option label="Q1" :value="1" />
            <el-option label="Q2" :value="2" />
            <el-option label="Q3" :value="3" />
            <el-option label="Q4" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="visitForm.plannedStartAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="visitForm.plannedEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="摘要" class="wide-item">
          <el-input v-model="visitForm.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="结论" class="wide-item">
          <el-input v-model="visitForm.conclusion" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitVisit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignmentDialogVisible" :title="editingAssignment ? '编辑安排' : '新增安排'" width="620px">
      <el-form :model="assignmentForm" label-position="top" class="dialog-grid">
        <el-form-item label="日期时间"><el-date-picker v-model="assignmentForm.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="楼层"><el-input v-model="assignmentForm.floorName" /></el-form-item>
        <el-form-item label="事项" class="wide-item"><el-input v-model="assignmentForm.taskItem" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="assignmentForm.ownerName" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="assignmentForm.status" /></el-form-item>
        <el-form-item label="备注" class="wide-item"><el-input v-model="assignmentForm.notes" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="assignmentDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAssignment">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="personnelReportDialogVisible" title="人员报备" width="min(860px, calc(100vw - 32px))">
      <div class="personnel-report-head">
        <span>共 {{ selectedVisit?.personnel?.length || 0 }} 名报备人员</span>
        <el-button size="small" type="primary" :disabled="isTemporary" @click="openPersonnelCreate">
          <el-icon><Plus /></el-icon>
          新增人员
        </el-button>
      </div>
      <el-table :data="selectedVisit?.personnel || []" empty-text="暂无人员报备" max-height="480" border>
        <el-table-column prop="personName" label="姓名" width="120" />
        <el-table-column prop="phone" label="电话" width="150" />
        <el-table-column prop="roleName" label="角色" min-width="140" />
        <el-table-column prop="notes" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="isTemporary" @click="openPersonnelEdit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="isTemporary" @click="removePersonnel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="personnelReportDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="personnelDialogVisible" :title="editingPersonnel ? '编辑人员' : '新增人员'" width="560px">
      <el-form :model="personnelForm" label-position="top" class="dialog-grid">
        <el-form-item label="姓名"><el-input v-model="personnelForm.personName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="personnelForm.phone" /></el-form-item>
        <el-form-item label="角色"><el-input v-model="personnelForm.roleName" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="personnelForm.notes" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="personnelDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitPersonnel">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="findingDialogVisible" :title="editingFinding ? '编辑现场记录' : '新增现场记录'" width="760px">
      <el-form :model="findingForm" label-position="top" class="dialog-grid">
        <el-form-item label="楼层"><el-input v-model="findingForm.floorName" /></el-form-item>
        <el-form-item label="位置/区域"><el-input v-model="findingForm.areaName" /></el-form-item>
        <el-form-item label="问题描述" class="wide-item"><el-input v-model="findingForm.issueDescription" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="处理情况" class="wide-item"><el-input v-model="findingForm.handlingResult" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="完成情况"><el-input v-model="findingForm.completionStatus" placeholder="如：已解决 / 待整改" /></el-form-item>
        <el-form-item label="发现时间"><el-date-picker v-model="findingForm.foundAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="原因分析" class="wide-item"><el-input v-model="findingForm.causeAnalysis" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="后续动作" class="wide-item"><el-input v-model="findingForm.followUpAction" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="需要报价"><el-switch v-model="findingForm.quoteRequired" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="入知识库"><el-switch v-model="findingForm.knowledgeIncluded" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="findingDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitFinding">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="quoteDialogVisible" :title="editingQuote ? '编辑报价项' : '新增报价项'" width="680px">
      <el-form :model="quoteForm" label-position="top" class="dialog-grid">
        <el-form-item label="区域"><el-input v-model="quoteForm.areaName" /></el-form-item>
        <el-form-item label="事项"><el-input v-model="quoteForm.itemName" /></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="quoteForm.quantity" :min="0" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="quoteForm.unitName" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="quoteForm.unitPrice" :min="0" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="quoteForm.amount" :min="0" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="备注" class="wide-item"><el-input v-model="quoteForm.notes" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="quoteDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitQuote">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="closeDialogVisible" title="关闭运维批次" width="640px">
      <el-form :model="closeForm" label-position="top">
        <el-form-item label="运维摘要"><el-input v-model="closeForm.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="运维结论"><el-input v-model="closeForm.conclusion" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="实际结束时间"><el-date-picker v-model="closeForm.actualEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="closeDialogVisible = false">取消</el-button><el-button type="success" :loading="saving" @click="submitClose">关闭并沉淀</el-button></template>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  closeMaintenanceVisit,
  createMaintenanceAssignment,
  createMaintenanceFinding,
  createMaintenancePersonnel,
  createMaintenanceQuoteItem,
  createMaintenanceVisit,
  deleteMaintenanceAssignment,
  deleteMaintenanceFinding,
  deleteMaintenancePersonnel,
  deleteMaintenanceQuoteItem,
  exportMaintenanceReportExcel,
  getMaintenanceOverview,
  getMaintenanceVisit,
  getMaintenanceVisitByFinding,
  getMaintenanceVisits,
  startMaintenanceVisit,
  updateMaintenanceAssignment,
  updateMaintenanceFinding,
  updateMaintenancePersonnel,
  updateMaintenanceQuoteItem,
  updateMaintenanceVisit,
  uploadMaintenanceFindingAttachment
} from '@/api/maintenance'
import { getProjects } from '@/api/projects'
import { useUserStore } from '@/stores/user'
import { formatDateTime, numberText, toDateTimeQuery } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const activeTab = ref('overview')
const dateRange = ref([])
const visits = ref([])
const projects = ref([])
const selectedVisit = ref(null)
const selectedFindingId = ref(null)
const findingKeyword = ref('')
const findingStatusFilter = ref('')
const quoteKeyword = ref('')
const scheduleDate = ref('')
const overview = ref(emptyOverview())
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const filters = reactive({ projectId: undefined, status: '', year: new Date().getFullYear(), quarter: undefined, keyword: '' })

const visitDialogVisible = ref(false)
const batchDrawerVisible = ref(false)
const assignmentDialogVisible = ref(false)
const personnelReportDialogVisible = ref(false)
const personnelDialogVisible = ref(false)
const findingDialogVisible = ref(false)
const quoteDialogVisible = ref(false)
const closeDialogVisible = ref(false)
const editingVisit = ref(null)
const editingAssignment = ref(null)
const editingPersonnel = ref(null)
const editingFinding = ref(null)
const editingQuote = ref(null)

const visitForm = reactive(defaultVisitForm())
const assignmentForm = reactive(defaultAssignmentForm())
const personnelForm = reactive(defaultPersonnelForm())
const findingForm = reactive(defaultFindingForm())
const quoteForm = reactive(defaultQuoteForm())
const closeForm = reactive({ summary: '', conclusion: '', actualEndAt: '' })

const checklistCategoryDefinitions = [
  {
    key: 'lighting',
    label: '灯具状态异常',
    tone: 'tone-lighting',
    items: [
      { key: 'lighting-on', label: '灯具点亮状态' },
      { key: 'lighting-stability', label: '灯具闪烁 / 亮度异常' },
      { key: 'lighting-power', label: '灯具回路 / 供电状态' }
    ]
  },
  {
    key: 'panel',
    label: '面板控制异常',
    tone: 'tone-panel',
    items: [
      { key: 'panel-response', label: '面板控制响应' },
      { key: 'panel-scene', label: '场景 / 时控执行' },
      { key: 'panel-signal', label: '面板信号 / 连接状态' }
    ]
  },
  {
    key: 'gateway',
    label: '网关状态异常',
    tone: 'tone-gateway',
    items: [
      { key: 'gateway-online', label: '网关在线状态' },
      { key: 'gateway-connection', label: '网关连接 / 配置状态' }
    ]
  },
  {
    key: 'device',
    label: '设备/驱动/通讯异常',
    tone: 'tone-device',
    items: [
      { key: 'device-cu', label: 'CU / Bridge 通讯状态' },
      { key: 'device-driver', label: '设备 / 驱动工作状态' },
      { key: 'device-network', label: '路由器 / 交换机状态' }
    ]
  },
  {
    key: 'cloud',
    label: '云端控制异常',
    tone: 'tone-cloud',
    items: [
      { key: 'cloud-control', label: '云端 / 远程控制状态' },
      { key: 'cloud-data', label: '能耗 / 数据上报状态' }
    ]
  }
]

const isTemporary = computed(() => userStore.isTemporary)
const selectableProjects = computed(() => (projects.value || []).filter(project => (project.projectLevel || 'PROJECT') === 'PROJECT' && Number(project.isActive) === 1))
const visitStats = computed(() => {
  const findings = selectedVisit.value?.findings || []
  const quoteItems = selectedVisit.value?.quoteItems || []
  const unresolved = findings.filter(item => isUnresolvedStatus(item.completionStatus)).length
  const quoteRequired = findings.filter(item => Number(item.quoteRequired) === 1).length
  const knowledge = findings.filter(item => Number(item.knowledgeIncluded) === 1).length
  return {
    assignments: selectedVisit.value?.assignments?.length || 0,
    personnel: selectedVisit.value?.personnel?.length || 0,
    findings: findings.length,
    unresolved,
    resolved: Math.max(findings.length - unresolved, 0),
    quoteRequired,
    knowledge,
    quoteItems: quoteItems.length
  }
})
const quoteTotal = computed(() => (selectedVisit.value?.quoteItems || []).reduce((sum, item) => sum + amountValue(item), 0))
const quoteAreaCount = computed(() => new Set((filteredQuoteItems.value || []).map(item => item.areaName).filter(Boolean)).size)
const visitProgress = computed(() => {
  const checks = [
    visitStats.value.assignments > 0,
    visitStats.value.personnel > 0,
    checklistRecords.value.length > 0,
    visitStats.value.findings > 0,
    visitStats.value.quoteItems > 0 || visitStats.value.quoteRequired === 0,
    selectedVisit.value?.summary || selectedVisit.value?.conclusion
  ]
  return Math.round((checks.filter(Boolean).length / checks.length) * 100)
})
const visitKpis = computed(() => [
  { label: '前事项安排', value: numberText(visitStats.value.assignments), note: `${visitStats.value.personnel} 名报备人员` },
  { label: '检查记录', value: numberText(visitStats.value.findings), note: `${visitStats.value.unresolved} 条待整改`, className: visitStats.value.unresolved ? 'warning-text' : 'success-text' },
  { label: '知识沉淀', value: numberText(visitStats.value.knowledge), note: '来源 OPERATIONS', className: 'success-text' },
  { label: '报价合计', value: moneyText(quoteTotal.value), note: `${visitStats.value.quoteItems} 个报价项` }
])
const tabSteps = computed(() => [
  { tab: 'assignments', title: '运维前安排', icon: 'Calendar', countText: `${visitStats.value.assignments} 条安排`, done: visitStats.value.assignments > 0 },
  { tab: 'checklist', title: '检查清单', icon: 'Checked', countText: `${checklistRecords.value.length} 条检查记录`, done: checklistRecords.value.length > 0 },
  { tab: 'findings', title: '检查详细记录', icon: 'Tickets', countText: `${visitStats.value.findings} 条记录`, done: visitStats.value.findings > 0 },
  { tab: 'quotes', title: '备品报价', icon: 'Goods', countText: moneyText(quoteTotal.value), done: visitStats.value.quoteItems > 0 },
  { tab: 'report', title: '运维报告', icon: 'Document', countText: selectedVisit.value?.status === 'CLOSED' ? '可归档' : '可预览', done: Boolean(selectedVisit.value?.summary || selectedVisit.value?.conclusion) }
])
const focusFindings = computed(() => {
  return [...(selectedVisit.value?.findings || [])]
    .sort((a, b) => findingPriority(b) - findingPriority(a))
    .slice(0, 6)
})
const recentActivities = computed(() => {
  const rows = []
  ;(selectedVisit.value?.assignments || []).slice(0, 2).forEach(item => rows.push({ key: `a-${item.id}`, time: formatDateTime(item.scheduledAt), text: `${item.ownerName || '未分配'}：${item.taskItem || '运维安排'}` }))
  ;(selectedVisit.value?.findings || []).slice(0, 4).forEach(item => rows.push({ key: `f-${item.id}`, time: formatDateTime(item.foundAt), text: `${item.completionStatus || '记录'}：${item.issueDescription || item.areaName || '现场记录'}` }))
  return rows.length ? rows.slice(0, 5) : [{ key: 'empty', time: '-', text: '暂无动态' }]
})
const sourceSummary = computed(() => {
  const map = new Map()
  ;(selectedVisit.value?.sourceFiles || []).forEach(file => map.set(file.fileType || 'FILE', (map.get(file.fileType || 'FILE') || 0) + 1))
  return [...map.entries()].map(([fileType, count]) => ({ fileType, count }))
})
const findingStatusOptions = computed(() => [...new Set((selectedVisit.value?.findings || []).map(item => item.completionStatus).filter(Boolean))])
const filteredFindings = computed(() => {
  const keyword = findingKeyword.value.trim().toLowerCase()
  return (selectedVisit.value?.findings || []).filter(item => {
    const hitKeyword = !keyword || [item.floorName, item.areaName, item.issueDescription, item.handlingResult, item.followUpAction].some(value => String(value || '').toLowerCase().includes(keyword))
    const hitStatus = !findingStatusFilter.value || item.completionStatus === findingStatusFilter.value
    return hitKeyword && hitStatus
  })
})
const selectedFinding = computed(() => {
  const rows = selectedVisit.value?.findings || []
  return rows.find(item => item.id === selectedFindingId.value) || filteredFindings.value[0] || rows[0] || null
})
const checklistRecords = computed(() => selectedVisit.value?.findings || [])
const checklistFindings = computed(() => checklistRecords.value.filter(isChecklistAbnormalFinding))
const checklistFloors = computed(() => {
  const floors = [...new Set(checklistRecords.value.map(checklistFloorName).filter(Boolean))]
  return floors.length ? floors.sort(compareFloorNames) : ['未标注']
})
const checklistInspectionDate = computed(() => {
  const counts = new Map()
  checklistRecords.value.forEach(item => {
    const key = dateKey(item.foundAt)
    if (key) counts.set(key, (counts.get(key) || 0) + 1)
  })
  if (counts.size) return [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0]
  return dateText(selectedVisit.value?.plannedStartAt)
})
const checklistMatrixRows = computed(() => checklistCategoryDefinitions.flatMap((category, categoryIndex) => {
  return category.items.map((checkItem, itemIndex) => ({
    key: checkItem.key,
    label: checkItem.label,
    category: category.label,
    categoryIndex: String(categoryIndex + 1).padStart(2, '0'),
    categoryRowSpan: itemIndex === 0 ? category.items.length : 0,
    cells: checklistFloors.value.map(floor => {
      const issues = checklistFindings.value
        .filter(item => checklistFloorName(item) === floor && checklistItemKey(item) === checkItem.key)
        .map(finding => ({ finding, label: checklistIssueLabel(finding), open: isUnresolvedStatus(finding.completionStatus) }))
      const hasOpen = issues.some(item => item.open)
      return {
        floor,
        issues,
        hasOpen,
        label: issues.length > 1 ? `${issues[0].label} +${issues.length - 1}` : (issues[0]?.label || ''),
        tooltip: issues.map(item => `${item.label}：${cleanChecklistText(item.finding.issueDescription) || cleanChecklistText(item.finding.handlingResult) || '-'}`).join('\n')
      }
    })
  }))
}))
const checklistCategorySummary = computed(() => checklistCategoryDefinitions.map(category => {
  const rows = checklistFindings.value.filter(item => checklistCategoryKey(item) === category.key)
  const open = rows.filter(item => isUnresolvedStatus(item.completionStatus)).length
  const floors = [...new Set(rows.map(checklistFloorName))]
  const issueTypes = [...new Set(rows.map(checklistIssueLabel))]
  const floorText = floors.length > 6 ? `${floors.slice(0, 6).join('、')} 等 ${floors.length} 个楼层` : floors.join('、')
  const issueText = issueTypes.slice(0, 4).join('、') || '现场异常'
  return {
    ...category,
    total: rows.length,
    open,
    resolved: rows.length - open,
    summary: `共 ${rows.length} 项，主要涉及 ${floorText || '未标注区域'}；表现为 ${issueText}。已解决 ${rows.length - open} 项，待解决 ${open} 项。`
  }
}))
const checklistSummaryTotals = computed(() => {
  const abnormal = checklistFindings.value.length
  const open = checklistFindings.value.filter(item => isUnresolvedStatus(item.completionStatus)).length
  return {
    total: checklistRecords.value.length,
    normal: Math.max(checklistRecords.value.length - abnormal, 0),
    abnormal,
    open,
    resolved: Math.max(abnormal - open, 0)
  }
})
const resolvedChecklistSummary = computed(() => {
  const rows = checklistFindings.value.filter(item => !isUnresolvedStatus(item.completionStatus))
  if (!rows.length) return '本次检查暂无已解决问题。'
  const categories = checklistCategoryDefinitions
    .filter(category => rows.some(item => checklistCategoryKey(item) === category.key))
    .map(category => category.label.replace('异常', ''))
  const actions = [...new Set(rows
    .map(item => meaningfulChecklistText(item.handlingResult) || meaningfulChecklistText(item.followUpAction))
    .filter(Boolean))]
    .slice(0, 3)
    .map(shortChecklistText)
  const actionText = actions.length ? `主要处理包括：${actions.join('；')}。` : '相关问题已完成处理并恢复正常。'
  return `已完成 ${rows.length} 项问题处理，涉及 ${categories.join('、') || '现场设备'}。${actionText}建议按检查日期安排复核。`
})
const pendingChecklistCategorySolutions = computed(() => checklistCategoryDefinitions.flatMap(category => {
  const rows = checklistFindings.value.filter(item => checklistCategoryKey(item) === category.key && isUnresolvedStatus(item.completionStatus))
  if (!rows.length) return []
  const floors = [...new Set(rows.map(checklistFloorName))]
  const issueTypes = [...new Set(rows.map(checklistIssueLabel))]
  const floorText = floors.length > 6 ? `${floors.slice(0, 6).join('、')} 等 ${floors.length} 个楼层` : floors.join('、')
  const suggestedActions = [...new Set(rows
    .map(item => meaningfulChecklistText(item.followUpAction) || meaningfulChecklistText(item.handlingResult))
    .filter(Boolean))]
    .slice(0, 2)
    .map(shortChecklistText)
  return [{
    key: category.key,
    label: category.label,
    overview: `共 ${rows.length} 项，涉及 ${floorText || '未标注区域'}，主要表现为 ${issueTypes.slice(0, 4).join('、') || '现场异常'}。`,
    impact: checklistCustomerImpact(category.key),
    solution: suggestedActions.length ? `${suggestedActions.join('；')}。` : checklistDefaultSolution(category.key)
  }]
}))
const scheduleHours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18]
const scheduleItems = computed(() => {
  return (selectedVisit.value?.assignments || [])
    .flatMap((item, index) => {
      if (scheduleDate.value && dateKey(item.scheduledAt) !== scheduleDate.value) return []
      return toScheduleItem(item, index)
    })
    .sort((a, b) => a.startMinutes - b.startMinutes || a.id - b.id)
})
const scheduleHourGroups = computed(() => scheduleHours.map((hour, index) => {
  const items = scheduleItems.value.filter(item => Math.floor(item.startMinutes / 60) === hour)
  const floors = [...new Set(items.map(item => item.floorText))]
  const titles = [...new Set(items.map(item => item.title))]
  return {
    hour,
    items,
    raw: items[0]?.raw,
    timeText: [...new Set(items.map(item => item.timeText))].join(' / '),
    floorText: floors.join('、'),
    title: titles.length > 1 ? `${titles[0]} 等 ${items.length} 项` : (titles[0] || ''),
    tooltip: items.map(item => `${item.timeText} ${item.floorText} ${item.title}`).join('\n'),
    tone: `tone-${(index % 5) + 1}`
  }
}))
const filteredQuoteItems = computed(() => {
  const keyword = quoteKeyword.value.trim().toLowerCase()
  return (selectedVisit.value?.quoteItems || []).filter(item => !keyword || [item.areaName, item.itemName, item.notes].some(value => String(value || '').toLowerCase().includes(keyword)))
})
const comparisonCards = computed(() => [
  { label: '本次异常', value: numberText(visitStats.value.unresolved), note: '待整改 / 待确认项', className: visitStats.value.unresolved ? 'warning-text' : 'success-text' },
  { label: '重复问题', value: numberText(repeatIssueCount()), note: '同区域相似问题', className: repeatIssueCount() ? 'warning-text' : '' },
  { label: '需报价项', value: numberText(visitStats.value.quoteRequired), note: '可转报价清单', className: visitStats.value.quoteRequired ? 'warning-text' : '' },
  { label: '知识沉淀', value: numberText(visitStats.value.knowledge), note: '已标记 OPERATIONS', className: 'success-text' }
])
const reportSummary = computed(() => `本次运维共形成 ${visitStats.value.assignments} 条前期安排、${visitStats.value.findings} 条检查详细记录、${visitStats.value.quoteItems} 个报价项。待整改/待确认记录 ${visitStats.value.unresolved} 条，已沉淀知识库 ${visitStats.value.knowledge} 条。`)

function emptyOverview() {
  return { totalVisits: 0, inProgressVisits: 0, unresolvedFindings: 0, quoteTotalAmount: 0 }
}

function defaultVisitForm() {
  const now = new Date()
  return { projectId: undefined, visitTitle: '', serviceYear: now.getFullYear(), serviceQuarter: Math.floor(now.getMonth() / 3) + 1, plannedStartAt: '', plannedEndAt: '', summary: '', conclusion: '' }
}

function defaultAssignmentForm() {
  return { scheduledAt: '', floorName: '', taskItem: '', ownerName: '', status: 'PENDING', notes: '' }
}

function defaultPersonnelForm() {
  return { personName: '', phone: '', roleName: '现场运维', notes: '' }
}

function defaultFindingForm() {
  return { floorName: '', areaName: '', issueDescription: '', handlingResult: '', completionStatus: '待处理', causeAnalysis: '', followUpAction: '', quoteRequired: 0, knowledgeIncluded: 1, foundAt: '' }
}

function defaultQuoteForm() {
  return { areaName: '', itemName: '', quantity: 1, unitName: '项', unitPrice: 0, amount: 0, notes: '' }
}

async function loadProjects() {
  const res = await getProjects()
  projects.value = res.data || []
}

async function loadVisits() {
  loading.value = true
  try {
    const params = buildParams()
    const [visitRes, overviewRes] = await Promise.all([
      getMaintenanceVisits({ ...params, page: pagination.page, pageSize: pagination.pageSize }),
      getMaintenanceOverview(params)
    ])
    visits.value = visitRes.data?.items || []
    pagination.total = visitRes.data?.total || 0
    overview.value = overviewRes.data || emptyOverview()
    if (selectedVisit.value) {
      const exists = visits.value.find(item => item.id === selectedVisit.value.id)
      if (exists) await loadVisitDetail(exists.id)
    }
  } finally {
    loading.value = false
  }
}

function buildParams() {
  return {
    ...filters,
    startDate: toDateTimeQuery(dateRange.value?.[0]),
    endDate: toDateTimeQuery(dateRange.value?.[1], true)
  }
}

async function selectVisit(row) {
  await loadVisitDetail(row.id)
  batchDrawerVisible.value = false
}

async function loadVisitDetail(id) {
  const res = await getMaintenanceVisit(id)
  selectedVisit.value = res.data
  syncScheduleDate(selectedVisit.value?.assignments || [])
  if (!selectedFindingId.value && selectedVisit.value?.findings?.length) {
    selectedFindingId.value = selectedVisit.value.findings[0].id
  }
}

async function loadVisitByFinding(findingId) {
  if (!findingId) return false
  const res = await getMaintenanceVisitByFinding(findingId)
  selectedVisit.value = res.data
  selectedFindingId.value = Number(findingId)
  activeTab.value = 'findings'
  return true
}

function goTab(tab) {
  activeTab.value = tab
}

function selectFinding(row) {
  selectedFindingId.value = row?.id || null
  activeTab.value = 'findings'
  if (row?.id) {
    router.replace({ path: '/maintenance', query: { findingId: row.id } })
  }
}

function applyFilters() {
  pagination.page = 1
  loadVisits()
}

function resetFilters() {
  Object.assign(filters, { projectId: undefined, status: '', year: new Date().getFullYear(), quarter: undefined, keyword: '' })
  dateRange.value = []
  pagination.page = 1
  selectedVisit.value = null
  loadVisits()
}

function handlePageChange(page) {
  pagination.page = page
  loadVisits()
}

function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadVisits()
}

function openVisitCreate() {
  editingVisit.value = null
  Object.assign(visitForm, defaultVisitForm())
  visitDialogVisible.value = true
}

function openVisitEdit(row) {
  editingVisit.value = row
  Object.assign(visitForm, {
    projectId: row.projectId,
    visitTitle: row.visitTitle || '',
    serviceYear: row.serviceYear,
    serviceQuarter: row.serviceQuarter,
    plannedStartAt: row.plannedStartAt || '',
    plannedEndAt: row.plannedEndAt || '',
    summary: row.summary || '',
    conclusion: row.conclusion || ''
  })
  visitDialogVisible.value = true
}

async function submitVisit() {
  if (!visitForm.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  saving.value = true
  try {
    const res = editingVisit.value ? await updateMaintenanceVisit(editingVisit.value.id, visitForm) : await createMaintenanceVisit(visitForm)
    visitDialogVisible.value = false
    ElMessage.success('运维批次已保存')
    await loadVisits()
    await loadVisitDetail(res.data.id)
  } finally {
    saving.value = false
  }
}

async function startVisit() {
  if (!selectedVisit.value) return
  const res = await startMaintenanceVisit(selectedVisit.value.id)
  selectedVisit.value = res.data
  ElMessage.success('运维批次已开始')
  await loadVisits()
}

function openCloseDialog() {
  Object.assign(closeForm, {
    summary: selectedVisit.value?.summary || '',
    conclusion: selectedVisit.value?.conclusion || '',
    actualEndAt: defaultDateTime()
  })
  closeDialogVisible.value = true
}

async function submitClose() {
  saving.value = true
  try {
    const res = await closeMaintenanceVisit(selectedVisit.value.id, closeForm)
    selectedVisit.value = res.data
    closeDialogVisible.value = false
    ElMessage.success('运维批次已关闭，完成项已沉淀到知识库')
    await loadVisits()
  } finally {
    saving.value = false
  }
}

function openAssignmentCreate() {
  editingAssignment.value = null
  Object.assign(assignmentForm, defaultAssignmentForm())
  assignmentDialogVisible.value = true
}

function openAssignmentEdit(row) {
  editingAssignment.value = row
  Object.assign(assignmentForm, { ...row })
  assignmentDialogVisible.value = true
}

async function submitAssignment() {
  saving.value = true
  try {
    if (editingAssignment.value) await updateMaintenanceAssignment(selectedVisit.value.id, editingAssignment.value.id, assignmentForm)
    else await createMaintenanceAssignment(selectedVisit.value.id, assignmentForm)
    assignmentDialogVisible.value = false
    await refreshSelected('安排已保存')
  } finally {
    saving.value = false
  }
}

async function removeAssignment(row) {
  await confirmRemove('确认删除该安排？')
  await deleteMaintenanceAssignment(selectedVisit.value.id, row.id)
  await refreshSelected('安排已删除')
}

function openPersonnelCreate() {
  editingPersonnel.value = null
  Object.assign(personnelForm, defaultPersonnelForm())
  personnelDialogVisible.value = true
}

function openPersonnelEdit(row) {
  editingPersonnel.value = row
  Object.assign(personnelForm, { ...row })
  personnelDialogVisible.value = true
}

async function submitPersonnel() {
  saving.value = true
  try {
    if (editingPersonnel.value) await updateMaintenancePersonnel(selectedVisit.value.id, editingPersonnel.value.id, personnelForm)
    else await createMaintenancePersonnel(selectedVisit.value.id, personnelForm)
    personnelDialogVisible.value = false
    await refreshSelected('人员已保存')
  } finally {
    saving.value = false
  }
}

async function removePersonnel(row) {
  await confirmRemove('确认删除该人员？')
  await deleteMaintenancePersonnel(selectedVisit.value.id, row.id)
  await refreshSelected('人员已删除')
}

function openFindingCreate() {
  editingFinding.value = null
  Object.assign(findingForm, defaultFindingForm(), { foundAt: defaultDateTime() })
  findingDialogVisible.value = true
}

function openFindingEdit(row) {
  editingFinding.value = row
  Object.assign(findingForm, { ...row })
  findingDialogVisible.value = true
}

async function submitFinding() {
  if (!findingForm.issueDescription) {
    ElMessage.warning('请输入问题描述')
    return
  }
  saving.value = true
  try {
    if (editingFinding.value) await updateMaintenanceFinding(selectedVisit.value.id, editingFinding.value.id, findingForm)
    else await createMaintenanceFinding(selectedVisit.value.id, findingForm)
    findingDialogVisible.value = false
    await refreshSelected('现场记录已保存')
    if (editingFinding.value) selectedFindingId.value = editingFinding.value.id
    await loadVisits()
  } finally {
    saving.value = false
  }
}

async function removeFinding(row) {
  await confirmRemove('确认删除该现场记录？')
  await deleteMaintenanceFinding(selectedVisit.value.id, row.id)
  await refreshSelected('现场记录已删除')
  await loadVisits()
}

async function handleAttachmentChange(row, uploadFile) {
  if (!uploadFile?.raw) return
  await uploadMaintenanceFindingAttachment(row.id, uploadFile.raw)
  await refreshSelected('附件已上传')
}

function openQuoteCreate() {
  editingQuote.value = null
  Object.assign(quoteForm, defaultQuoteForm())
  quoteDialogVisible.value = true
}

function openQuoteEdit(row) {
  editingQuote.value = row
  Object.assign(quoteForm, { ...row })
  quoteDialogVisible.value = true
}

async function submitQuote() {
  saving.value = true
  try {
    const data = { ...quoteForm, amount: Number(quoteForm.amount || 0) || Number(quoteForm.quantity || 0) * Number(quoteForm.unitPrice || 0) }
    if (editingQuote.value) await updateMaintenanceQuoteItem(selectedVisit.value.id, editingQuote.value.id, data)
    else await createMaintenanceQuoteItem(selectedVisit.value.id, data)
    quoteDialogVisible.value = false
    await refreshSelected('报价项已保存')
    await loadVisits()
  } finally {
    saving.value = false
  }
}

async function removeQuote(row) {
  await confirmRemove('确认删除该报价项？')
  await deleteMaintenanceQuoteItem(selectedVisit.value.id, row.id)
  await refreshSelected('报价项已删除')
  await loadVisits()
}

async function exportReport() {
  if (!selectedVisit.value) return
  exporting.value = true
  try {
    const response = await exportMaintenanceReportExcel(selectedVisit.value.id)
    const blob = response.data
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `运维报告-${selectedVisit.value.visitNo || selectedVisit.value.id}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

async function refreshSelected(message) {
  await loadVisitDetail(selectedVisit.value.id)
  if (message) ElMessage.success(message)
}

function confirmRemove(message) {
  return ElMessageBox.confirm(message, '删除确认', { type: 'warning' })
}

function visitStatusMeta(status) {
  return {
    PLANNED: { label: '计划中', type: 'info' },
    IN_PROGRESS: { label: '进行中', type: 'primary' },
    CLOSED: { label: '已关闭', type: 'success' }
  }[status] || { label: status || '-', type: 'info' }
}

function assignmentStatusText(status) {
  return {
    PENDING: '待开始',
    IN_PROGRESS: '进行中',
    DONE: '已完成',
    CANCELLED: '已取消',
    IMPORTED: '已导入'
  }[status] || status || '待确认'
}

function isUnresolvedStatus(status) {
  const text = String(status || '')
  return !text || ['待', '未', '整改', '确认', '处理中', '记录'].some(keyword => text.includes(keyword)) && !['已完成', '已解决', '完成', '解决'].some(keyword => text.includes(keyword))
}

function findingStatusType(status) {
  if (isUnresolvedStatus(status)) return 'warning'
  return String(status || '').includes('关闭') ? 'info' : 'success'
}

function findingPriority(item) {
  return (isUnresolvedStatus(item.completionStatus) ? 10 : 0)
    + (Number(item.quoteRequired) === 1 ? 4 : 0)
    + (Number(item.knowledgeIncluded) === 1 ? 2 : 0)
}

function inferCheckCategory(item) {
  return checklistCategoryDefinitions.find(category => category.key === checklistCategoryKey(item))?.label || '灯具状态异常'
}

function checklistCategoryKey(item) {
  const text = findingSearchText(item)
  if (/云端(?:无法|不能|不可|控制异常)|平台异常|APP异常|远程控制异常/i.test(text)) return 'cloud'
  if (/GW|网关|gateway/i.test(text)) return 'gateway'
  if (/CU|Bridge|驱动|通讯|通信|离线|路由器|交换机|传感器|设备/i.test(text)) return 'device'
  if (/云端|平台|APP|远程|后台|服务器/i.test(text)) return 'cloud'
  if (/面板|开关|时控|场景|按键|不受控|无法控制|控制失效/i.test(text)) return 'panel'
  return 'lighting'
}

function isChecklistAbnormalFinding(item) {
  const text = findingSearchText(item)
  const status = String(item?.completionStatus || '')
  if (/[×✕✖]/.test(text)) return true
  if (/[√✓]/.test(text) && !/[×✕✖]/.test(text)) return false
  if (/正常|通过|无异常|无问题/.test(status)) return false
  return Boolean(cleanChecklistText(item?.issueDescription) || cleanChecklistText(item?.handlingResult))
}

function cleanChecklistText(value) {
  const text = String(value || '')
    .replace(/[√✓×✕✖]/g, '')
    .replace(/[；;、,，\s]+$/g, '')
    .trim()
  return text && !/^(正常|无异常|无问题|无)$/.test(text) ? text : ''
}

function shortChecklistText(value, maxLength = 42) {
  const text = cleanChecklistText(value)
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}

function meaningfulChecklistText(value) {
  const text = cleanChecklistText(value)
  return text.replace(/[.。…;；,，\s]/g, '').length >= 4 ? text : ''
}

function checklistItemKey(item) {
  const text = findingSearchText(item)
  const category = checklistCategoryKey(item)
  if (category === 'lighting') {
    if (/闪烁|频闪|忽明忽暗|亮度|变暗/i.test(text)) return 'lighting-stability'
    if (/回路|供电|电源|断电|线路/i.test(text)) return 'lighting-power'
    return 'lighting-on'
  }
  if (category === 'panel') {
    if (/场景|时控|定时|自动/i.test(text)) return 'panel-scene'
    if (/信号|连接|丢失|脱落/i.test(text)) return 'panel-signal'
    return 'panel-response'
  }
  if (category === 'gateway') {
    if (/配置|连接|接线|网络|路由/i.test(text)) return 'gateway-connection'
    return 'gateway-online'
  }
  if (category === 'device') {
    if (/路由器|交换机|4G|网络/i.test(text)) return 'device-network'
    if (/驱动|设备|传感器|故障|损坏/i.test(text)) return 'device-driver'
    return 'device-cu'
  }
  if (/能耗|数据|上报|上传/i.test(text)) return 'cloud-data'
  return 'cloud-control'
}

function checklistFloorName(item) {
  const explicit = String(item?.floorName || '').trim()
  if (explicit && !/^[-—]+$/.test(explicit)) return explicit
  const text = [item?.areaName, item?.issueDescription].filter(Boolean).join(' ')
  const floorMatch = text.match(/(?:^|[^0-9])(\d{1,2})\s*F(?:[^0-9]|$)/i)
  if (floorMatch) return `${Number(floorMatch[1])}F`
  const roomMatch = text.match(/(?:^|[^0-9])(\d{2})(\d{2})(?:[^0-9]|$)/)
  if (roomMatch) return `${Number(roomMatch[1])}F`
  return '未标注'
}

function compareFloorNames(a, b) {
  if (a === '未标注') return 1
  if (b === '未标注') return -1
  const aNumber = Number(String(a).match(/\d+/)?.[0] || Number.MAX_SAFE_INTEGER)
  const bNumber = Number(String(b).match(/\d+/)?.[0] || Number.MAX_SAFE_INTEGER)
  return aNumber - bNumber || String(a).localeCompare(String(b), 'zh-CN')
}

function checklistIssueLabel(item) {
  const text = findingSearchText(item)
  if (/不亮|熄灭|无法点亮/i.test(text)) return '灯具不亮'
  if (/闪烁|频闪|忽明忽暗/i.test(text)) return '灯具闪烁'
  if (/不受控|无法控制|控制失效/i.test(text)) return '不受控'
  if (/离线|掉线/i.test(text)) return '设备离线'
  if (/通讯|通信/i.test(text)) return '通讯异常'
  if (/网关/i.test(text)) return '网关异常'
  if (/驱动|故障|损坏/i.test(text)) return '设备故障'
  if (/云端|平台|远程/i.test(text)) return '云端异常'
  return inferCheckCategory(item).replace('状态异常', '异常').replace('控制异常', '异常').slice(0, 8)
}

function checklistCustomerImpact(categoryKey) {
  return {
    lighting: '影响区域照明效果、照度体验或正常使用。',
    panel: '客户可能无法通过现场面板正常开关或切换场景。',
    gateway: '影响设备在线监测、集中控制及故障定位效率。',
    device: '可能导致局部设备失控、通讯中断或运行不稳定。',
    cloud: '影响远程控制、状态查看或能耗数据统计。'
  }[categoryKey] || '可能影响现场照明系统的正常使用。'
}

function checklistDefaultSolution(categoryKey) {
  return {
    lighting: '现场复核灯具、电源与回路状态，确认故障点后维修或更换。',
    panel: '检查面板供电、地址与控制关系，重新配置并完成现场复测。',
    gateway: '检查网关供电、网络和配置，必要时重启、重配或更换设备。',
    device: '排查接线、通讯链路及设备状态，修复连接或更换异常部件。',
    cloud: '核对云端配置、设备映射和网络状态，修正后验证远程控制及数据上报。'
  }[categoryKey] || '安排现场复核并根据检查结果完成修复。'
}

function findingSearchText(item) {
  return [item?.floorName, item?.areaName, item?.issueDescription, item?.handlingResult, item?.causeAnalysis, item?.followUpAction].filter(Boolean).join(' ')
}

function toScheduleItem(item, index) {
  const date = normalizedAssignmentDate(item, index)
  const duration = 60
  return {
    id: item.id,
    raw: item,
    startMinutes: date.getHours() * 60 + date.getMinutes(),
    floorText: item.floorName || '未标注楼层',
    title: item.taskItem || '巡检安排',
    timeText: `${timeText(date)} - ${timeText(new Date(date.getTime() + duration * 60000))}`,
    tone: `tone-${(index % 5) + 1}`
  }
}

function syncScheduleDate(assignments) {
  const counts = new Map()
  assignments.forEach(item => {
    const key = dateKey(item.scheduledAt)
    if (key) counts.set(key, (counts.get(key) || 0) + 1)
  })
  if (!counts.size) {
    scheduleDate.value = ''
    return
  }
  if (counts.has(scheduleDate.value)) return
  scheduleDate.value = [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0]
}

function parseDate(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function timeText(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function dateText(value) {
  const date = parseDate(value)
  if (!date) return '-'
  const pad = item => String(item).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function onlyTimeText(value) {
  const date = parseDate(value)
  return date ? timeText(date) : '-'
}

function assignmentDisplayTime(item, index) {
  const date = parseDate(item.scheduledAt)
  if (date && !isMidnight(date)) return timeText(date)
  const fallback = normalizedAssignmentDate(item, index)
  return `${timeText(fallback)}*`
}

function normalizedAssignmentDate(item, index) {
  const date = parseDate(item.scheduledAt) || parseDate(selectedVisit.value?.plannedStartAt) || new Date()
  if (!isMidnight(date)) return date
  const routeIndex = index % 8
  const copy = new Date(date)
  copy.setHours(9 + routeIndex, routeIndex % 2 === 0 ? 0 : 30, 0, 0)
  return copy
}

function isMidnight(date) {
  return date.getHours() === 0 && date.getMinutes() === 0
}

function dateKey(value) {
  const date = parseDate(value)
  if (!date) return ''
  const pad = item => String(item).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function amountValue(item) {
  const amount = Number(item?.amount || 0)
  if (amount) return amount
  return Number(item?.quantity || 0) * Number(item?.unitPrice || 0)
}

function repeatIssueCount() {
  const seen = new Map()
  let repeats = 0
  ;(selectedVisit.value?.findings || []).forEach(item => {
    const key = [item.floorName, item.areaName, inferCheckCategory(item)].filter(Boolean).join('|')
    if (!key) return
    const count = seen.get(key) || 0
    if (count === 1) repeats += 1
    seen.set(key, count + 1)
  })
  return repeats
}

function quoteSummaryMethod({ columns, data }) {
  return columns.map((column, index) => {
    if (index === 0) return '合计'
    if (column.property === 'amount') return moneyText(data.reduce((sum, item) => sum + amountValue(item), 0))
    return ''
  })
}

function moneyText(value) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}

function defaultDateTime() {
  const date = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

onMounted(async () => {
  await loadProjects()
  await loadVisits()
  if (route.query.findingId) {
    await loadVisitByFinding(route.query.findingId)
  } else if (!selectedVisit.value && visits.value.length) {
    await loadVisitDetail(visits.value[0].id)
  }
})
</script>

<style scoped>
.maintenance-page {
  --ops-line: #e5ebf5;
  --ops-muted: #667893;
  --ops-text: #1d3354;
}

.visit-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 20px;
  align-items: center;
  padding: 20px 22px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.breadcrumb-line {
  color: var(--ops-muted);
  font-size: 12px;
  font-weight: 800;
}

.visit-title-block h1 {
  margin: 7px 0 6px;
  color: var(--ops-text);
  font-size: 24px;
  line-height: 1.25;
}

.visit-title-block p {
  margin: 0;
  color: var(--ops-muted);
}

.visit-hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(86px, auto));
  gap: 8px;
}

.visit-hero-stats > div {
  display: grid;
  gap: 3px;
  min-width: 86px;
  padding: 10px 12px;
  border: 1px solid #dfe8f5;
  border-radius: 8px;
  background: #fff;
}

.visit-hero-stats span {
  color: var(--ops-muted);
  font-size: 12px;
}

.visit-hero-stats strong {
  color: var(--ops-text);
  font-size: 18px;
}

.template-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ops-line);
  flex-wrap: wrap;
}

.batch-drawer-body {
  display: grid;
  gap: 16px;
}

.drawer-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.drawer-metrics article,
.drawer-filter,
.drawer-table {
  padding: 16px;
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  background: #fff;
}

.drawer-metrics article {
  display: grid;
  gap: 6px;
}

.drawer-metrics span {
  color: var(--ops-muted);
  font-size: 12px;
}

.drawer-metrics strong {
  color: var(--ops-text);
  font-size: 24px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 18px;
}

.filter-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.wide-item {
  grid-column: span 2;
}

.action-item {
  align-self: end;
}

.toolbar-actions,
.filter-actions,
.table-actions,
.tab-toolbar,
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
  color: var(--ops-muted);
}

.detail-card {
  display: grid;
  gap: 14px;
}

.tab-toolbar {
  margin-bottom: 14px;
  color: var(--ops-muted);
}

.template-stack {
  display: grid;
  gap: 18px;
}

.template-table-panel :deep(.el-table th.el-table__cell) {
  background: #f3f7fc;
  color: #253653;
  font-weight: 800;
}

.checklist-page {
  display: grid;
  gap: 18px;
}

.checklist-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.checklist-section-head > div:first-child {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.section-index {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: #eaf2ff;
  color: #1769d2;
  font-size: 12px;
  font-weight: 800;
}

.checklist-head-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: 8px;
}

.checklist-head-meta > div {
  display: grid;
  gap: 4px;
  padding: 9px 12px;
  border: 1px solid #e1e9f4;
  border-radius: 6px;
  background: #f8fbff;
}

.checklist-head-meta span {
  color: var(--ops-muted);
  font-size: 11px;
}

.checklist-head-meta strong {
  color: var(--ops-text);
  font-size: 15px;
}

.checklist-legend {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 12px;
  color: #52647e;
  font-size: 12px;
  flex-wrap: wrap;
}

.checklist-legend > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.checklist-legend em {
  margin-left: auto;
  color: #8390a3;
  font-style: normal;
}

.legend-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.legend-dot.normal { background: #20a464; }
.legend-dot.resolved { background: #3478d4; }
.legend-dot.open { background: #dc4c55; }

.checklist-matrix-scroll {
  max-width: 100%;
  overflow: auto;
  border: 1px solid #dce5f1;
  border-radius: 6px;
}

.checklist-matrix {
  width: max-content;
  min-width: 100%;
  border-spacing: 0;
  border-collapse: separate;
  color: #243650;
  font-size: 12px;
}

.checklist-matrix th,
.checklist-matrix td {
  height: 46px;
  padding: 7px 8px;
  border-right: 1px solid #e3eaf4;
  border-bottom: 1px solid #e3eaf4;
  background: #fff;
  text-align: center;
  vertical-align: middle;
}

.checklist-matrix thead th {
  position: sticky;
  top: 0;
  z-index: 4;
  min-width: 106px;
  background: #eef4fb;
  color: #263955;
  font-weight: 800;
}

.checklist-matrix tr:last-child > * {
  border-bottom: 0;
}

.checklist-matrix tr > *:last-child {
  border-right: 0;
}

.matrix-category-column,
.matrix-category-cell {
  position: sticky;
  left: 0;
  z-index: 3;
  width: 150px;
  min-width: 150px !important;
}

.matrix-item-column,
.matrix-item-cell {
  position: sticky;
  left: 150px;
  z-index: 3;
  width: 220px;
  min-width: 220px !important;
}

.checklist-matrix thead .matrix-category-column,
.checklist-matrix thead .matrix-item-column {
  z-index: 6;
}

.matrix-category-cell {
  background: #f7faff !important;
}

.matrix-category-cell span,
.matrix-category-cell strong {
  display: block;
}

.matrix-category-cell span {
  margin-bottom: 5px;
  color: #8390a3;
  font-size: 10px;
}

.matrix-category-cell strong {
  color: #1d3354;
  line-height: 1.45;
}

.matrix-item-cell {
  background: #fbfdff !important;
  text-align: left !important;
  color: #42536c;
  font-weight: 700;
}

.matrix-status-cell {
  min-width: 106px;
}

.matrix-normal-mark {
  display: inline-grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border-radius: 50%;
  background: #e9f8f0;
  color: #178d55;
  font-size: 16px;
  font-weight: 800;
}

.matrix-issue-tag {
  width: 92px;
  min-height: 28px;
  padding: 5px 7px;
  border: 1px solid;
  border-radius: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.matrix-issue-tag.is-open {
  border-color: #ef9ca1;
  background: #fff0f1;
  color: #bd313c;
}

.matrix-issue-tag.is-resolved {
  border-color: #9bbce8;
  background: #eef5ff;
  color: #245fae;
}

.checklist-report-summary {
  padding: 14px 16px;
  border: 1px solid #e1e9f4;
  border-radius: 6px;
  background: #fbfdff;
}

.checklist-report-summary > p {
  margin: 0;
  color: #40516a;
  line-height: 1.8;
}

.condensed-category-list {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  padding: 12px 0 0;
  border-top: 1px solid #e5ebf3;
  list-style: none;
  counter-reset: category-summary;
}

.condensed-category-list li {
  display: grid;
  grid-template-columns: 5px minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
  counter-increment: category-summary;
}

.condensed-category-list li > span {
  border-radius: 3px;
  background: #7589a6;
}

.condensed-category-list li > span.tone-lighting { background: #e0a32f; }
.condensed-category-list li > span.tone-panel { background: #d16068; }
.condensed-category-list li > span.tone-gateway { background: #3478d4; }
.condensed-category-list li > span.tone-device { background: #2a9a83; }
.condensed-category-list li > span.tone-cloud { background: #7c68c5; }

.condensed-category-list p {
  margin: 0;
  color: #53647c;
  line-height: 1.7;
}

.condensed-category-list p::before {
  content: counter(category-summary) '. ';
  color: #8390a3;
  font-weight: 700;
}

.condensed-category-list strong {
  color: #243c59;
}

.concise-solution-summary {
  display: grid;
  gap: 0;
  border: 1px solid #e1e9f4;
  border-radius: 6px;
  background: #fff;
}

.concise-solution-summary > section {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 11px;
  padding: 14px 16px;
  border-bottom: 1px solid #e6ecf4;
}

.concise-solution-summary > section:last-child {
  border-bottom: 0;
}

.solution-number {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 5px;
  background: #e7f6ee;
  color: #14824d;
  font-size: 12px;
  font-weight: 800;
}

.solution-number.pending {
  background: #fff0f1;
  color: #bd313c;
}

.concise-solution-summary h4 {
  margin: 2px 0 7px;
  color: #213a57;
  font-size: 14px;
}

.concise-solution-summary section > div > p,
.pending-category-summary-list p {
  margin: 0;
  color: #53647c;
  line-height: 1.75;
}

.pending-category-summary-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pending-category-summary-list li {
  padding-bottom: 11px;
  border-bottom: 1px dashed #dfe7f1;
}

.pending-category-summary-list li:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.pending-category-summary-list strong {
  display: block;
  margin-bottom: 5px;
  color: #263f5d;
}

.pending-category-summary-list p span {
  color: #738199;
  font-size: 12px;
  font-weight: 700;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.info-pair {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  background: #f7faff;
}

.info-pair span {
  color: var(--ops-muted);
  font-size: 12px;
}

.info-pair strong,
.block-title {
  color: var(--ops-text);
  font-weight: 800;
}

.report-preview {
  display: grid;
  gap: 16px;
}

.import-report {
  display: grid;
  gap: 14px;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.report-grid > div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  background: #f7faff;
}

.report-grid span {
  color: var(--ops-muted);
  font-size: 12px;
}

.report-grid strong {
  color: var(--ops-text);
  font-size: 18px;
}

.import-root {
  margin: 2px 0;
}

.readable-text {
  margin: 8px 0 0;
  color: #253653;
  line-height: 1.8;
  white-space: pre-wrap;
}

.ops-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.overview-layout,
.report-layout,
.quote-layout,
.checklist-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: start;
}

.overview-main,
.overview-side {
  display: grid;
  gap: 16px;
}

.ops-panel {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(19, 42, 78, 0.05);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.ops-kpi-grid,
.step-grid,
.compare-grid,
.report-stat-grid,
.check-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.ops-kpi-card,
.step-card,
.compare-grid article,
.report-stat-grid div,
.check-summary div {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e4ebf6;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.ops-kpi-card {
  display: grid;
  gap: 7px;
}

.ops-kpi-card span,
.ops-kpi-card em,
.compare-grid span,
.compare-grid em,
.report-stat-grid span,
.check-summary span {
  color: var(--ops-muted);
  font-size: 12px;
  font-style: normal;
}

.ops-kpi-card strong,
.compare-grid strong,
.report-stat-grid strong,
.check-summary strong {
  color: var(--ops-text);
  font-size: 24px;
  line-height: 1.15;
}

.progress-inline {
  width: 180px;
}

.step-card {
  display: grid;
  gap: 8px;
  text-align: left;
  cursor: pointer;
  color: var(--ops-text);
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.step-card:hover,
.issue-focus-card:hover,
.finding-card:hover {
  border-color: #9fc4ff;
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(22, 119, 255, 0.10);
}

.step-card .el-icon {
  color: var(--primary);
  font-size: 20px;
}

.issue-focus-list {
  display: grid;
  gap: 10px;
}

.issue-focus-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e4ebf6;
  border-radius: 8px;
  background: #fbfdff;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.issue-focus-card > div:first-child,
.finding-card {
  min-width: 0;
}

.issue-focus-card strong,
.finding-card strong {
  display: block;
  color: var(--ops-text);
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-focus-card span,
.finding-card p,
.finding-meta,
.side-info-list span,
.activity-list span,
.source-mini-list span {
  color: var(--ops-muted);
  font-size: 12px;
}

.side-info-list {
  display: grid;
  gap: 10px;
}

.side-info-list > div,
.source-mini-list > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #edf2f8;
}

.side-info-list strong {
  text-align: right;
  word-break: break-word;
}

.activity-list {
  display: grid;
  gap: 10px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.activity-list li {
  display: grid;
  gap: 4px;
  padding-left: 12px;
  border-left: 3px solid #cfe0ff;
}

.assignment-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
  gap: 18px;
}

.schedule-panel {
  margin-top: 18px;
}

.personnel-report-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--ops-muted);
}

.schedule-board {
  overflow-x: auto;
  border: 1px solid #e3ebf5;
  border-radius: 8px;
  background: #fff;
}

.schedule-grid {
  display: grid;
  grid-template-columns: 140px minmax(1100px, 1fr);
  min-width: 1240px;
}

.schedule-corner,
.schedule-route-label {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  border-right: 1px solid #e3ebf5;
  background: #f8fbff;
  color: #263955;
  font-weight: 800;
}

.schedule-route-label {
  min-height: 104px;
  border-top: 1px solid #e3ebf5;
  line-height: 1.5;
}

.schedule-hours,
.schedule-hour-cells {
  display: grid;
  grid-template-columns: repeat(11, minmax(100px, 1fr));
}

.schedule-hours {
  background: #f8fbff;
}

.schedule-hours span {
  padding: 11px 0;
  border-left: 1px solid #e9eff7;
  text-align: center;
  color: #738199;
  font-size: 12px;
}

.schedule-hour-cells {
  min-height: 104px;
  border-top: 1px solid #e3ebf5;
  background: #fbfdff;
}

.schedule-hour-cell {
  min-width: 0;
  padding: 9px 6px;
  border-left: 1px solid #edf2f8;
}

.schedule-route-item {
  width: 100%;
  min-width: 0;
  height: 84px;
  display: grid;
  gap: 4px;
  align-content: center;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid;
  text-align: left;
  font-size: 12px;
  cursor: pointer;
  overflow: hidden;
}

.schedule-route-item span {
  color: #5f6f86;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-route-item strong {
  color: var(--ops-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-route-item em {
  color: #43536d;
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tone-1 { border-color: #7aa7ff; background: #eef5ff; }
.tone-2 { border-color: #71d3a3; background: #eefbf5; }
.tone-3 { border-color: #ffb76a; background: #fff6eb; }
.tone-4 { border-color: #b199ff; background: #f4f0ff; }
.tone-5 { border-color: #ff9ca0; background: #fff0f1; }

.inline-search {
  width: 260px;
}

.wide-search {
  width: 300px;
}

.status-filter {
  width: 150px;
}

.floor-chip-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.floor-chip-list button {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e4ebf6;
  border-radius: 8px;
  background: #f8fbff;
  cursor: pointer;
}

.floor-chip-list span {
  color: var(--ops-muted);
}

.finding-workspace {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.1fr);
  gap: 18px;
}

.template-finding-workspace {
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
}

.finding-list {
  display: grid;
  gap: 10px;
  max-height: 680px;
  overflow: auto;
  padding-right: 4px;
}

.finding-card {
  padding: 14px;
  border: 1px solid #e3ebf5;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.finding-card.active {
  border-color: var(--primary);
  background: #f4f8ff;
}

.finding-card-head,
.finding-meta,
.detail-badges {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.finding-card p {
  margin: 8px 0;
  line-height: 1.6;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.finding-detail-panel {
  min-width: 0;
  padding: 18px;
  border: 1px solid #dfe8f5;
  border-radius: 8px;
  background: #fbfdff;
}

.detail-section {
  display: grid;
  gap: 6px;
  padding: 14px 0;
  border-bottom: 1px solid #edf2f8;
}

.detail-section span,
.source-box span {
  color: var(--ops-muted);
  font-size: 12px;
  font-weight: 800;
}

.detail-section p {
  margin: 0;
  color: #253653;
  line-height: 1.75;
  white-space: pre-wrap;
}

.detail-badges {
  justify-content: flex-start;
  margin: 14px 0;
  flex-wrap: wrap;
}

.attachment-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.attachment-grid .el-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4ebf6;
}

.source-box {
  display: grid;
  gap: 5px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 8px;
  background: #f3f7fc;
  word-break: break-all;
}

.source-box em {
  color: var(--ops-muted);
  font-style: normal;
  font-size: 12px;
}

.compare-panel {
  margin-top: 18px;
}

.quote-summary-panel {
  position: sticky;
  top: 16px;
}

.quote-total {
  margin: 14px 0;
  color: var(--primary);
  font-size: 32px;
  font-weight: 900;
}

.full-button {
  width: 100%;
  margin-top: 14px;
}

.report-paper {
  min-width: 0;
  padding: 30px;
  border: 1px solid #dfe8f5;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(16, 38, 76, 0.08);
}

.report-cover {
  padding-bottom: 22px;
  border-bottom: 2px solid #e5edf8;
}

.report-cover span {
  color: var(--primary);
  font-weight: 800;
}

.report-cover h2 {
  margin: 10px 0 8px;
  color: var(--ops-text);
  font-size: 26px;
}

.report-cover p {
  margin: 0;
  color: var(--ops-muted);
}

.report-section {
  padding: 22px 0;
  border-bottom: 1px solid #edf2f8;
}

.report-section h3 {
  margin: 0 0 14px;
  color: var(--ops-text);
  font-size: 17px;
}

.report-stat-grid div {
  display: grid;
  gap: 6px;
}

.report-finding {
  padding: 12px 0;
  border-bottom: 1px dashed #dfe8f5;
}

.report-finding strong {
  color: var(--ops-text);
}

.report-finding p {
  margin: 6px 0 0;
  color: #43536d;
  line-height: 1.7;
}

.export-panel {
  display: grid;
  gap: 12px;
  position: sticky;
  top: 16px;
}

.source-table-title {
  margin-top: 8px;
  color: var(--ops-text);
  font-weight: 800;
}

@media (max-width: 1200px) {
  .filter-grid,
  .info-grid,
  .report-grid,
  .visit-hero,
  .drawer-metrics,
  .overview-layout,
  .report-layout,
  .quote-layout,
  .checklist-layout,
  .assignment-layout,
  .finding-workspace {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-layout,
  .report-layout,
  .quote-layout,
  .checklist-layout,
  .template-finding-workspace {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 720px) {
  .filter-grid,
  .dialog-grid,
  .info-grid,
  .report-grid,
  .visit-hero,
  .visit-hero-stats,
  .drawer-metrics,
  .ops-kpi-grid,
  .step-grid,
  .compare-grid,
  .report-stat-grid,
  .check-summary,
  .assignment-layout,
  .finding-workspace {
    grid-template-columns: 1fr;
  }

  .wide-item {
    grid-column: span 1;
  }

  .pager {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions,
  .tab-toolbar,
  .panel-head,
  .checklist-section-head,
  .issue-focus-card,
  .finding-card-head,
  .finding-meta {
    align-items: stretch;
    flex-direction: column;
  }

  .checklist-head-meta {
    grid-template-columns: 1fr;
  }

  .checklist-legend em {
    width: 100%;
    margin-left: 0;
  }

  .matrix-category-column,
  .matrix-category-cell {
    width: 108px;
    min-width: 108px !important;
  }

  .matrix-item-column,
  .matrix-item-cell {
    left: 108px;
    width: 164px;
    min-width: 164px !important;
  }

  .wide-search,
  .inline-search,
  .status-filter,
  .progress-inline {
    width: 100%;
  }

  .report-paper {
    padding: 18px;
  }

  .attachment-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
