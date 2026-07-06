<template>
  <div class="page-shell knowledge-page">
    <section class="kb-hero">
      <div>
        <div class="section-title">&#30693;&#35782;&#24211;&#31649;&#29702;</div>
        <p class="section-hint">&#25353;&#30693;&#35782;&#26469;&#28304;&#31649;&#29702;&#21487;&#22797;&#29992;&#32463;&#39564;&#12290;&#31532;&#20108;&#38454;&#27573;&#20808;&#20570;&#32467;&#26500;&#21270;&#30693;&#35782;&#27785;&#28096;&#65292;RAG&#12289;&#21521;&#37327;&#24211;&#21644;&#26234;&#33021;&#38382;&#31572;&#25918;&#21040;&#19979;&#19968;&#38454;&#27573;&#25509;&#20837;&#12290;</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="strategyVisible = true">
          <el-icon><InfoFilled /></el-icon>
          &#20998;&#22359;&#35828;&#26126;
        </el-button>
        <el-button @click="chunkSettingVisible = true">
          <el-icon><Setting /></el-icon>
          &#20998;&#22359;&#21442;&#25968;
        </el-button>
        <el-button :loading="syncing" @click="handleSyncClosed">
          <el-icon><Refresh /></el-icon>
          &#21516;&#27493;&#24050;&#20851;&#38381;&#38382;&#39064;
        </el-button>
      </div>
    </section>

    <section class="source-grid">
      <article v-for="source in sourceCards" :key="source.type" class="source-card">
        <div class="source-top">
          <div class="source-title-wrap">
            <div class="source-icon">
              <el-icon><component :is="source.icon" /></el-icon>
            </div>
            <div>
              <h3>{{ source.title }}</h3>
              <span>&#21521;&#37327;&#27169;&#22411;&#65306;BAAI/bge-m3</span>
            </div>
          </div>
          <el-dropdown trigger="click">
            <el-button class="more-btn" text>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openDocuments(source)">&#25991;&#26723;</el-dropdown-item>
                <el-dropdown-item @click="openQa(source)">&#38382;&#31572;&#39564;&#35777;</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <p class="source-desc">{{ source.description }}</p>

        <div class="source-meta">
          <span class="doc-pill">{{ numberText(sourceStats[source.type]?.total || 0) }} &#26465;&#30693;&#35782;</span>
          <span class="chunk-pill">Chunk {{ chunkSize }}</span>
          <span class="source-pill">{{ source.sourceLabel }}</span>
        </div>

        <div class="source-actions">
          <el-button class="card-action ghost" size="small" @click="openDocuments(source)">
            <el-icon><Document /></el-icon>
            &#25991;&#26723;
          </el-button>
          <el-button class="card-action primary" size="small" type="primary" @click="openQa(source)">
            <el-icon><ChatDotRound /></el-icon>
            &#38382;&#31572;
          </el-button>
          <el-upload
            v-if="source.type === SOURCE_COMPANY_EXCEL"
            :show-file-list="false"
            :auto-upload="false"
            accept=".xlsx,.xls"
            :on-change="handleUploadChange"
          >
            <el-button class="card-action upload" size="small" type="success" :loading="uploading">
              <el-icon><Upload /></el-icon>
              &#19978;&#20256; Excel
            </el-button>
          </el-upload>
        </div>
      </article>
    </section>

    <section class="processing-card">
      <div class="processing-title">&#25991;&#26723;&#22788;&#29702;&#26041;&#26696;</div>
      <div class="processing-steps">
        <div v-for="step in processingSteps" :key="step.title" class="process-step">
          <strong>{{ step.title }}</strong>
          <span>{{ step.text }}</span>
        </div>
      </div>
    </section>

    <el-drawer v-model="documentsVisible" size="86%" destroy-on-close>
      <template #header>
        <div>
          <div class="drawer-title">{{ activeSource?.title || text.knowledgeDocs }}</div>
          <p class="section-hint">{{ activeSource?.description }}</p>
        </div>
      </template>

      <section class="section-card filter-card">
        <div class="toolbar">
          <div>
            <div class="section-title">&#25991;&#26723;&#21015;&#34920;</div>
            <p class="section-hint">&#38382;&#39064;&#21488;&#36134;&#21644; Excel &#34892;&#25968;&#25454;&#37117;&#32479;&#19968;&#36716;&#25104;&#32467;&#26500;&#21270;&#30693;&#35782;&#26465;&#30446;&#65292;&#21518;&#32493;&#21487;&#30452;&#25509;&#20316;&#20026;&#26816;&#32034;&#35821;&#26009;&#12290;</p>
          </div>
          <div class="toolbar-actions">
            <el-upload
              v-if="activeSource?.type === SOURCE_COMPANY_EXCEL"
              :show-file-list="false"
              :auto-upload="false"
              accept=".xlsx,.xls"
              :on-change="handleUploadChange"
            >
              <el-button type="success" :loading="uploading">
                <el-icon><Upload /></el-icon>
                &#19978;&#20256; Excel
              </el-button>
            </el-upload>
            <el-button type="primary" @click="applyFilters">
              <el-icon><Search /></el-icon>
              &#26597;&#35810;
            </el-button>
          </div>
        </div>

        <el-form label-position="top" class="filter-grid">
          <el-form-item :label="text.project">
            <el-select v-model="filters.projectId" :disabled="activeSource?.type === SOURCE_COMPANY_EXCEL" clearable filterable :placeholder="text.selectProject">
              <el-option v-for="project in selectableProjects" :key="project.id" :label="project.projectName" :value="project.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.faultCode">
            <el-select v-model="filters.faultCode" clearable :placeholder="text.selectFaultCode">
              <el-option v-for="item in faultCodeOptions" :key="item.value" :label="`${item.value} ${item.label}`" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.systemType">
            <el-select v-model="filters.systemType" :disabled="activeSource?.type === SOURCE_COMPANY_EXCEL" clearable filterable :placeholder="text.selectSystem">
              <el-option v-for="item in systemTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.causeCategory">
            <el-select v-model="filters.causeCategory" clearable filterable :placeholder="text.selectCause">
              <el-option v-for="item in causeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.tags">
            <el-input v-model="filters.tagKeyword" :placeholder="text.tagPlaceholder" @keyup.enter="applyFilters" />
          </el-form-item>
          <el-form-item :label="text.status">
            <el-select v-model="filters.status" clearable :placeholder="text.defaultPublished">
              <el-option :label="text.published" value="PUBLISHED" />
              <el-option :label="text.disabled" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.keyword" class="wide-item">
            <el-input v-model="filters.keyword" :placeholder="text.searchPlaceholder" @keyup.enter="applyFilters">
              <template #suffix><el-icon><Search /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item class="action-item">
            <div class="filter-actions">
              <el-button @click="resetFilters">{{ text.reset }}</el-button>
              <el-button type="primary" @click="applyFilters">{{ text.query }}</el-button>
            </div>
          </el-form-item>
        </el-form>
      </section>

      <section class="section-card">
        <el-table :data="knowledgeRows" v-loading="loading" row-key="id" :empty-text="text.noKnowledge" @row-click="openDetail">
          <el-table-column prop="title" :label="text.knowledgeTitle" min-width="220" show-overflow-tooltip />
          <el-table-column :label="text.source" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ sourceText(row) }}</template>
          </el-table-column>
          <el-table-column :label="text.faultCode" width="105">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.faultCode || 'OTHER' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="projectName" :label="text.project" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.projectName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="tags" :label="text.tags" min-width="180" show-overflow-tooltip />
          <el-table-column :label="text.chunkCount" width="95">
            <template #default="{ row }">{{ buildChunks(row).length }}</template>
          </el-table-column>
          <el-table-column :label="text.updateTime" width="170">
            <template #default="{ row }">{{ formatDateTime(row.updateTime || row.createTime) }}</template>
          </el-table-column>
          <el-table-column :label="text.actions" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">{{ text.view }}</el-button>
              <el-button link type="primary" @click.stop="openChunks(row)">{{ text.chunks }}</el-button>
              <el-button link :type="row.status === 'DISABLED' ? 'success' : 'warning'" @click.stop="toggleStatus(row)">
                {{ row.status === 'DISABLED' ? text.publish : text.disable }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <span>&#20849; {{ numberText(pagination.total) }} &#26465;&#30693;&#35782;</span>
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
    </el-drawer>

    <el-drawer v-model="detailVisible" size="720px" destroy-on-close>
      <template #header>
        <div>
          <div class="drawer-title">{{ detail?.title || text.knowledgeDetail }}</div>
          <p class="section-hint">{{ detailSourceText(detail) }}</p>
        </div>
      </template>

      <div v-if="detail" class="detail-stack">
        <section class="detail-card">
          <div class="detail-card-head">
            <div>
              <strong>{{ text.structureScore }}</strong>
              <p>{{ text.structureHint }}</p>
            </div>
            <el-progress type="circle" :width="72" :percentage="ragScore(detail)" />
          </div>
          <div class="info-grid">
            <div class="info-pair"><span>{{ text.source }}</span><strong>{{ sourceTypeText(detail.sourceType) }}</strong></div>
            <div class="info-pair"><span>{{ text.faultCode }}</span><strong>{{ detail.faultCode || 'OTHER' }}</strong></div>
            <div class="info-pair"><span>{{ text.keywordCount }}</span><strong>{{ extractKeywords(detail).length }}</strong></div>
            <div class="info-pair"><span>{{ text.questionCount }}</span><strong>{{ buildQuestions(detail).length }}</strong></div>
          </div>
        </section>

        <section class="detail-card" v-for="block in detailBlocks" :key="block.key">
          <div class="block-title">{{ block.title }}</div>
          <p class="readable-text">{{ valueText(detail[block.key]) }}</p>
        </section>

        <div class="drawer-actions">
          <el-button v-if="detail.issueId" @click="goIssue(detail)">
            <el-icon><Link /></el-icon>
            {{ text.sourceIssue }}
          </el-button>
          <el-button @click="openChunks(detail)">
            <el-icon><Document /></el-icon>
            {{ text.chunks }}
          </el-button>
          <el-button @click="openEdit">
            <el-icon><EditPen /></el-icon>
            {{ text.edit }}
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="chunkVisible" :title="text.chunkResult" width="920px">
      <div v-if="chunkTarget" class="chunk-layout">
        <div class="chunk-summary">
          <strong>{{ chunkTarget.title }}</strong>
          <span>{{ chunkTarget.faultCode || 'OTHER' }} / {{ buildChunks(chunkTarget).length }} {{ text.chunkUnit }}</span>
        </div>
        <div class="chunk-list">
          <article v-for="chunk in buildChunks(chunkTarget)" :key="chunk.index" class="chunk-card">
            <div class="chunk-head">
              <strong>#{{ chunk.index }} {{ chunk.title }}</strong>
              <el-tag size="small">{{ chunk.tokenEstimate }} tokens</el-tag>
            </div>
            <p>{{ chunk.content }}</p>
            <div class="chunk-meta">
              <span>{{ text.keywords }}</span>
              <el-tag v-for="keyword in chunk.keywords" :key="keyword" size="small" effect="plain">{{ keyword }}</el-tag>
            </div>
            <div class="chunk-questions">
              <span>{{ text.generatedQuestions }}</span>
              <ol>
                <li v-for="question in chunk.questions" :key="question">{{ question }}</li>
              </ol>
            </div>
          </article>
        </div>
      </div>
    </el-dialog>

    <el-drawer v-model="qaVisible" size="640px" destroy-on-close>
      <template #header>
        <div>
          <div class="drawer-title">{{ text.qaVerify }}</div>
          <p class="section-hint">{{ text.qaHint }}</p>
        </div>
      </template>
      <div class="qa-panel">
        <el-form label-position="top">
          <el-form-item :label="text.knowledgeBase">
            <el-input :model-value="qaSource?.title || text.allKnowledge" disabled />
          </el-form-item>
          <el-form-item :label="text.question">
            <el-input v-model="qaForm.question" type="textarea" :rows="4" :placeholder="text.questionPlaceholder" />
          </el-form-item>
          <el-button type="primary" @click="runQaSearch">
            <el-icon><Search /></el-icon>
            {{ text.searchVerify }}
          </el-button>
        </el-form>
        <div class="qa-results">
          <article v-for="result in qaResults" :key="result.id" class="qa-result">
            <div class="qa-result-head">
              <strong>{{ result.title }}</strong>
              <el-tag size="small">{{ result.score }} {{ text.score }}</el-tag>
            </div>
            <p>{{ result.solutionSummary || result.symptomSummary || '-' }}</p>
            <el-button link type="primary" @click="openDetail(result)">{{ text.viewKnowledge }}</el-button>
          </article>
          <el-empty v-if="qaSearched && !qaResults.length" :description="text.noQaResult" />
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="strategyVisible" :title="text.strategyTitle" width="760px">
      <div class="strategy-list">
        <article v-for="strategy in strategies" :key="strategy.name" class="strategy-card">
          <strong>{{ strategy.name }}</strong>
          <p>{{ strategy.scene }}</p>
          <div><span>{{ text.pros }}</span>{{ strategy.pros }}</div>
          <div><span>{{ text.cons }}</span>{{ strategy.cons }}</div>
        </article>
      </div>
    </el-dialog>

    <el-dialog v-model="chunkSettingVisible" :title="text.chunkSettings" width="520px">
      <el-form label-position="top">
        <el-form-item :label="text.chunkSize">
          <el-input-number v-model="chunkSize" :min="256" :max="1024" :step="128" />
        </el-form-item>
        <el-form-item :label="text.keywordExtractCount">
          <el-input-number v-model="keywordCount" :min="3" :max="10" />
        </el-form-item>
        <el-form-item :label="text.questionGenerateCount">
          <el-input-number v-model="questionCount" :min="1" :max="5" />
        </el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog v-model="editVisible" :title="text.editKnowledge" width="760px">
      <el-form :model="editForm" label-position="top" class="edit-form">
        <el-form-item :label="text.title">
          <el-input v-model="editForm.title" maxlength="255" show-word-limit />
        </el-form-item>
        <div class="dialog-grid">
          <el-form-item :label="text.faultCode">
            <el-select v-model="editForm.faultCode">
              <el-option v-for="item in faultCodeOptions" :key="item.value" :label="`${item.value} ${item.label}`" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.tags">
            <el-input v-model="editForm.tags" :placeholder="text.tagsPlaceholder" />
          </el-form-item>
        </div>
        <el-form-item v-for="block in detailBlocks" :key="block.key" :label="block.title">
          <el-input v-model="editForm[block.key]" type="textarea" :rows="block.rows" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ text.cancel }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">{{ text.save }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Collection,
  Document,
  EditPen,
  Files,
  InfoFilled,
  Link,
  MoreFilled,
  Refresh,
  Search,
  Setting,
  Upload
} from '@element-plus/icons-vue'
import {
  disableKnowledge,
  getKnowledgeDetail,
  getKnowledgeList,
  publishKnowledge,
  syncClosedKnowledge,
  updateKnowledge,
  uploadKnowledgeDocument
} from '@/api/knowledge'
import { getProjects } from '@/api/projects'
import { getRuleOptions } from '@/api/ruleOptions'
import { formatDateTime, numberText } from '@/utils/format'

const SOURCE_ISSUE_LEDGER = 'ISSUE_LEDGER'
const SOURCE_COMPANY_EXCEL = 'COMPANY_EXCEL'

const zh = {
  issueLedgerTitle: '\u95ee\u9898\u53f0\u8d26\u77e5\u8bc6\u5e93',
  companyExcelTitle: '\u516c\u53f8\u95ee\u9898\u7ecf\u9a8c\u5e93',
  issueSource: '\u65e5\u5e38\u5ba2\u6237\u53cd\u9988\u95ee\u9898',
  companySource: '\u5386\u53f2\u95ee\u9898\u7ecf\u9a8c Excel',
  issueDesc: '\u7531\u95ed\u73af\u95ee\u9898\u81ea\u52a8\u6c89\u6dc0\uff0c\u627f\u8f7d\u5ba2\u6237\u73b0\u573a\u53cd\u9988\u3001\u6392\u67e5\u8fc7\u7a0b\u3001\u539f\u56e0\u5206\u6790\u548c\u5904\u7406\u65b9\u6848\u3002',
  companyDesc: '\u7531\u516c\u53f8\u6574\u7406\u7684\u95ee\u9898\u7ecf\u9a8c\u8868\u5bfc\u5165\uff0c\u6bcf\u4e2a\u5de5\u4f5c\u8868\u4f5c\u4e3a\u5206\u7ec4\uff0c\u6bcf\u884c\u8f6c\u4e3a\u4e00\u6761\u53ef\u68c0\u7d22\u77e5\u8bc6\u3002'
}

const text = {
  knowledgeDocs: '\u77e5\u8bc6\u6587\u6863',
  project: '\u9879\u76ee',
  selectProject: '\u9009\u62e9\u9879\u76ee',
  faultCode: '\u6545\u969c\u4ee3\u7801',
  selectFaultCode: '\u9009\u62e9\u6545\u969c\u4ee3\u7801',
  systemType: '\u7cfb\u7edf/\u8bbe\u5907',
  selectSystem: '\u9009\u62e9\u7cfb\u7edf',
  causeCategory: '\u539f\u56e0\u5206\u7c7b',
  selectCause: '\u9009\u62e9\u539f\u56e0',
  tags: '\u6807\u7b7e',
  tagPlaceholder: '\u8f93\u5165\u6807\u7b7e\u5173\u952e\u8bcd',
  status: '\u72b6\u6001',
  defaultPublished: '\u9ed8\u8ba4\u53ea\u770b\u5df2\u53d1\u5e03',
  published: '\u5df2\u53d1\u5e03',
  disabled: '\u5df2\u505c\u7528',
  keyword: '\u5173\u952e\u8bcd',
  searchPlaceholder: '\u641c\u7d22\u6807\u9898\u3001\u73b0\u8c61\u3001\u539f\u56e0\u3001\u65b9\u6848\u3001\u9884\u9632\u63aa\u65bd',
  reset: '\u91cd\u7f6e',
  query: '\u67e5\u8be2',
  noKnowledge: '\u6682\u65e0\u77e5\u8bc6\u6587\u6863',
  knowledgeTitle: '\u77e5\u8bc6\u6807\u9898',
  source: '\u6765\u6e90',
  chunkCount: '\u5206\u5757\u6570\u91cf',
  updateTime: '\u66f4\u65b0\u65f6\u95f4',
  actions: '\u64cd\u4f5c',
  view: '\u67e5\u770b',
  chunks: '\u5206\u5757',
  publish: '\u53d1\u5e03',
  disable: '\u505c\u7528',
  knowledgeDetail: '\u77e5\u8bc6\u8be6\u60c5',
  structureScore: '\u7ed3\u6784\u5316\u5b8c\u6574\u5ea6',
  structureHint: '\u5b57\u6bb5\u8d8a\u5b8c\u6574\uff0c\u540e\u7eed\u5411\u91cf\u68c0\u7d22\u548c\u95ee\u7b54\u6548\u679c\u8d8a\u7a33\u5b9a\u3002',
  keywordCount: '\u5173\u952e\u8bcd\u6570',
  questionCount: '\u751f\u6210\u95ee\u9898',
  sourceIssue: '\u6765\u6e90\u95ee\u9898',
  edit: '\u7f16\u8f91',
  chunkResult: '\u5206\u5757\u7ed3\u679c\u67e5\u770b',
  chunkUnit: '\u4e2a\u5206\u5757',
  keywords: '\u5173\u952e\u8bcd',
  generatedQuestions: '\u751f\u6210\u95ee\u9898',
  qaVerify: '\u95ee\u7b54\u9a8c\u8bc1',
  qaHint: '\u5f53\u524d\u662f\u5173\u952e\u8bcd\u68c0\u7d22\u6a21\u62df\uff0c\u7528\u4e8e\u9a8c\u8bc1\u77e5\u8bc6\u7ec4\u7ec7\u8d28\u91cf\uff1b\u7b2c\u4e09\u9636\u6bb5\u518d\u63a5\u5165 embedding\u3001\u5411\u91cf\u5e93\u548c\u5927\u6a21\u578b\u3002',
  knowledgeBase: '\u77e5\u8bc6\u5e93',
  allKnowledge: '\u5168\u90e8\u77e5\u8bc6\u5e93',
  question: '\u95ee\u9898',
  questionPlaceholder: '\u4f8b\u5982\uff1a\u7f51\u5173\u79bb\u7ebf\u5e94\u8be5\u600e\u4e48\u6392\u67e5\uff1f',
  searchVerify: '\u68c0\u7d22\u9a8c\u8bc1',
  score: '\u5206',
  viewKnowledge: '\u67e5\u770b\u77e5\u8bc6',
  noQaResult: '\u6682\u65e0\u547d\u4e2d\u7ed3\u679c\uff0c\u8bf7\u8c03\u6574\u95ee\u9898\u6216\u5148\u5bfc\u5165\u77e5\u8bc6',
  strategyTitle: '\u5206\u5757\u65b9\u5f0f\u8bf4\u660e',
  pros: '\u4f18\u70b9',
  cons: '\u7f3a\u70b9',
  chunkSettings: '\u589e\u5f3a\u5206\u5757\u53c2\u6570',
  chunkSize: '\u5206\u5757\u5927\u5c0f',
  keywordExtractCount: '\u5173\u952e\u8bcd\u63d0\u53d6\u6570\u91cf',
  questionGenerateCount: '\u751f\u6210\u95ee\u9898\u6570\u91cf',
  editKnowledge: '\u7f16\u8f91\u77e5\u8bc6\u6761\u76ee',
  title: '\u6807\u9898',
  tagsPlaceholder: '\u591a\u4e2a\u6807\u7b7e\u7528\u9017\u53f7\u5206\u9694',
  cancel: '\u53d6\u6d88',
  save: '\u4fdd\u5b58'
}

const router = useRouter()
const loading = ref(false)
const syncing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const documentsVisible = ref(false)
const detailVisible = ref(false)
const editVisible = ref(false)
const chunkVisible = ref(false)
const qaVisible = ref(false)
const qaSearched = ref(false)
const strategyVisible = ref(false)
const chunkSettingVisible = ref(false)
const knowledgeRows = ref([])
const qaRows = ref([])
const projects = ref([])
const ruleOptions = ref({})
const detail = ref(null)
const chunkTarget = ref(null)
const qaResults = ref([])
const activeSource = ref(null)
const qaSource = ref(null)
const sourceStats = ref({})
const chunkSize = ref(512)
const keywordCount = ref(5)
const questionCount = ref(3)
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const filters = reactive({ projectId: undefined, systemType: '', faultCode: '', tagKeyword: '', causeCategory: '', keyword: '', status: '' })
const qaForm = reactive({ question: '' })
const editForm = reactive({ title: '', faultCode: 'OTHER', symptomSummary: '', causeSummary: '', solutionSummary: '', preventionSummary: '', tags: '' })

const sourceCards = [
  {
    type: SOURCE_ISSUE_LEDGER,
    title: zh.issueLedgerTitle,
    sourceLabel: zh.issueSource,
    description: zh.issueDesc,
    icon: Collection
  },
  {
    type: SOURCE_COMPANY_EXCEL,
    title: zh.companyExcelTitle,
    sourceLabel: zh.companySource,
    description: zh.companyDesc,
    icon: Files
  }
]

const faultCodeOptions = [
  { value: 'COM', label: '\u901a\u8baf' },
  { value: 'DEV', label: '\u8bbe\u5907' },
  { value: 'CFG', label: '\u914d\u7f6e' },
  { value: 'SW', label: '\u8f6f\u4ef6' },
  { value: 'PWR', label: '\u7535\u6e90' },
  { value: 'ENV', label: '\u73af\u5883' },
  { value: 'OTHER', label: '\u5176\u4ed6' }
]

const detailBlocks = [
  { key: 'symptomSummary', title: '\u95ee\u9898\u73b0\u8c61', rows: 3 },
  { key: 'causeSummary', title: '\u539f\u56e0\u5206\u6790', rows: 3 },
  { key: 'solutionSummary', title: '\u5904\u7406\u65b9\u6848', rows: 4 },
  { key: 'preventionSummary', title: '\u9884\u9632\u63aa\u65bd', rows: 3 }
]

const processingSteps = [
  { title: '\u89e3\u6790', text: '\u4e0a\u4f20 Excel \u540e\u8bfb\u53d6\u6240\u6709\u5de5\u4f5c\u8868\uff0c\u81ea\u52a8\u8bc6\u522b\u8868\u5934\u548c\u6709\u6548\u6570\u636e\u884c\u3002' },
  { title: '\u6620\u5c04', text: '\u628a\u95ee\u9898\u73b0\u8c61\u3001\u5206\u7c7b\u3001\u6392\u67e5\u65b9\u5411\u3001\u5904\u7406\u65b9\u6848\u3001\u5907\u6ce8\u7b49\u5217\u6620\u5c04\u5230\u7edf\u4e00\u77e5\u8bc6\u5b57\u6bb5\u3002' },
  { title: '\u5f52\u7c7b', text: '\u6309\u5173\u952e\u8bcd\u751f\u6210\u6545\u969c\u4ee3\u7801\u548c\u6807\u7b7e\uff0c\u4fdd\u7559\u6587\u4ef6\u540d\u3001\u5de5\u4f5c\u8868\u3001\u884c\u53f7\u4f5c\u4e3a\u6765\u6e90\u3002' },
  { title: '\u590d\u7528', text: '\u540c\u4e00\u6587\u4ef6\u540c\u4e00\u5de5\u4f5c\u8868\u540c\u884c\u518d\u6b21\u4e0a\u4f20\u65f6\u66f4\u65b0\u539f\u77e5\u8bc6\uff0c\u907f\u514d\u91cd\u590d\u5165\u5e93\u3002' }
]

const strategies = [
  { name: '\u7ed3\u6784\u5316\u6848\u4f8b\u5206\u5757', scene: '\u5f53\u524d\u9879\u76ee\u9ed8\u8ba4\u7b56\u7565\uff0c\u6309\u73b0\u8c61\u3001\u539f\u56e0\u3001\u65b9\u6848\u3001\u9884\u9632\u63aa\u65bd\u5207\u5206\u3002', pros: '\u4e0d\u6253\u65ad\u4e1a\u52a1\u8bed\u4e49\uff0c\u9002\u5408\u8fd0\u7ef4\u590d\u76d8\u548c\u76f8\u4f3c\u6848\u4f8b\u68c0\u7d22\u3002', cons: '\u4f9d\u8d56\u95ed\u73af\u5b57\u6bb5\u548c Excel \u5217\u5185\u5bb9\u8d28\u91cf\u3002' },
  { name: '\u901a\u7528\u6587\u6863\u5206\u5757 general', scene: '\u7b2c\u4e09\u9636\u6bb5\u7528\u4e8e\u5236\u5ea6\u3001\u6280\u672f\u6587\u6863\u3001\u9879\u76ee\u8d44\u6599\u3002', pros: '\u4fdd\u7559\u6bb5\u843d\u8fb9\u754c\uff0c\u9002\u5408\u591a\u6570\u975e\u7ed3\u6784\u5316\u6587\u6863\u3002', cons: '\u9700\u8981\u6587\u6863\u89e3\u6790\u3001\u5411\u91cf\u5316\u548c\u68c0\u7d22\u6d41\u6c34\u7ebf\u3002' },
  { name: '\u957f\u6587\u6863\u5206\u5757 book', scene: '\u7b2c\u4e09\u9636\u6bb5\u7528\u4e8e\u624b\u518c\u3001\u65b9\u6848\u4e66\u3001\u57f9\u8bad\u8d44\u6599\u3002', pros: '\u53ef\u4fdd\u7559\u7ae0\u8282\u7ed3\u6784\u548c\u4e0a\u4e0b\u6587\u3002', cons: '\u5b9e\u73b0\u6210\u672c\u3001\u5b58\u50a8\u91cf\u548c\u68c0\u7d22\u8c03\u4f18\u6210\u672c\u66f4\u9ad8\u3002' }
]

const selectableProjects = computed(() => (projects.value || []).filter(project => String(project.projectLevel) === 'PROJECT'))
const systemTypeOptions = computed(() => ruleOptions.value.systemTypes || [])
const causeOptions = computed(() => ruleOptions.value.causeCategories || [])

async function loadSourceStats() {
  const pairs = await Promise.all(sourceCards.map(async source => {
    const res = await getKnowledgeList({ sourceType: source.type, page: 1, pageSize: 1 })
    return [source.type, { total: res.data?.total || 0 }]
  }))
  sourceStats.value = Object.fromEntries(pairs)
}

async function loadKnowledge() {
  if (!activeSource.value) return
  loading.value = true
  try {
    const res = await getKnowledgeList({
      ...filters,
      sourceType: activeSource.value.type,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    knowledgeRows.value = res.data?.items || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  const res = await getProjects()
  projects.value = res.data || []
}

async function loadRuleOptions() {
  const res = await getRuleOptions()
  ruleOptions.value = res.data || {}
}

function openDocuments(source) {
  activeSource.value = source
  resetFilters(false)
  documentsVisible.value = true
  loadKnowledge()
}

function applyFilters() {
  pagination.page = 1
  loadKnowledge()
}

function resetFilters(shouldLoad = true) {
  Object.assign(filters, { projectId: undefined, systemType: '', faultCode: '', tagKeyword: '', causeCategory: '', keyword: '', status: '' })
  pagination.page = 1
  if (shouldLoad) loadKnowledge()
}

function handlePageChange(page) {
  pagination.page = page
  loadKnowledge()
}

function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadKnowledge()
}

async function openDetail(row) {
  const res = await getKnowledgeDetail(row.id)
  detail.value = res.data
  detailVisible.value = true
}

function openChunks(row) {
  chunkTarget.value = row
  chunkVisible.value = true
}

function openEdit() {
  Object.assign(editForm, {
    title: detail.value?.title || '',
    faultCode: detail.value?.faultCode || 'OTHER',
    symptomSummary: detail.value?.symptomSummary || '',
    causeSummary: detail.value?.causeSummary || '',
    solutionSummary: detail.value?.solutionSummary || '',
    preventionSummary: detail.value?.preventionSummary || '',
    tags: detail.value?.tags || ''
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!detail.value?.id) return
  saving.value = true
  try {
    const res = await updateKnowledge(detail.value.id, { ...editForm })
    detail.value = res.data
    editVisible.value = false
    ElMessage.success('\u77e5\u8bc6\u6761\u76ee\u5df2\u4fdd\u5b58')
    await Promise.all([loadKnowledge(), loadSourceStats()])
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const disabled = row.status === 'DISABLED'
  await ElMessageBox.confirm(
    disabled ? '\u786e\u8ba4\u53d1\u5e03\u8be5\u77e5\u8bc6\u6761\u76ee\uff1f' : '\u786e\u8ba4\u505c\u7528\u8be5\u77e5\u8bc6\u6761\u76ee\uff1f\u9ed8\u8ba4\u5217\u8868\u5c06\u4e0d\u518d\u5c55\u793a\u505c\u7528\u6761\u76ee\u3002',
    disabled ? '\u53d1\u5e03\u77e5\u8bc6' : '\u505c\u7528\u77e5\u8bc6'
  )
  const res = disabled ? await publishKnowledge(row.id) : await disableKnowledge(row.id)
  if (detail.value?.id === row.id) detail.value = res.data
  ElMessage.success(disabled ? '\u77e5\u8bc6\u6761\u76ee\u5df2\u53d1\u5e03' : '\u77e5\u8bc6\u6761\u76ee\u5df2\u505c\u7528')
  await Promise.all([loadKnowledge(), loadSourceStats()])
}

async function handleSyncClosed() {
  syncing.value = true
  try {
    const res = await syncClosedKnowledge()
    ElMessage.success(`\u5df2\u540c\u6b65 ${numberText(res.data || 0)} \u6761\u5df2\u5173\u95ed\u95ee\u9898`)
    await Promise.all([loadKnowledge(), loadSourceStats()])
  } finally {
    syncing.value = false
  }
}

async function handleUploadChange(uploadFile) {
  if (!uploadFile?.raw) return
  uploading.value = true
  try {
    const res = await uploadKnowledgeDocument(uploadFile.raw)
    ElMessage.success(`\u5df2\u5bfc\u5165 ${numberText(res.data || 0)} \u6761\u77e5\u8bc6`)
    await Promise.all([loadSourceStats(), activeSource.value ? loadKnowledge() : Promise.resolve()])
  } finally {
    uploading.value = false
  }
}

async function openQa(source) {
  qaSource.value = source
  qaForm.question = ''
  qaResults.value = []
  qaSearched.value = false
  qaVisible.value = true
  const res = await getKnowledgeList({ sourceType: source.type, page: 1, pageSize: 100 })
  qaRows.value = res.data?.items || []
}

function runQaSearch() {
  const question = String(qaForm.question || '').trim()
  if (!question) {
    ElMessage.warning('\u8bf7\u5148\u8f93\u5165\u8981\u9a8c\u8bc1\u7684\u95ee\u9898')
    return
  }
  qaSearched.value = true
  const tokens = splitText(question)
  qaResults.value = qaRows.value
    .map(row => ({ ...row, score: scoreRow(row, tokens) }))
    .filter(row => row.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 5)
}

function scoreRow(row, tokens) {
  const haystack = [row.title, row.symptomSummary, row.causeSummary, row.solutionSummary, row.preventionSummary, row.tags, row.projectName, row.sourceSheet].join(' ').toLowerCase()
  return tokens.reduce((score, token) => score + (haystack.includes(token.toLowerCase()) ? 10 : 0), 0)
}

function buildChunks(row) {
  return detailBlocks
    .map(block => [block.title, row[block.key]])
    .filter(([, content]) => hasText(content))
    .map(([title, content], index) => ({
      index: index + 1,
      title,
      content,
      tokenEstimate: estimateTokens(content),
      keywords: extractKeywords(row).slice(0, keywordCount.value),
      questions: buildQuestions(row, title).slice(0, questionCount.value)
    }))
}

function extractKeywords(row) {
  const base = [row.faultCode, row.projectName, row.sourceSheet, row.tags, row.title, row.causeSummary, row.symptomSummary].filter(Boolean).join(' ')
  return Array.from(new Set(splitText(base))).slice(0, keywordCount.value)
}

function buildQuestions(row, section = '') {
  const subject = row.title || '\u8fd9\u4e2a\u95ee\u9898'
  return [
    `${subject} \u7684\u73b0\u8c61\u662f\u4ec0\u4e48\uff1f`,
    `${subject} \u7684\u539f\u56e0\u662f\u4ec0\u4e48\uff1f`,
    `${subject} \u5e94\u8be5\u5982\u4f55\u5904\u7406\uff1f`,
    `${subject} \u5982\u4f55\u9884\u9632\u590d\u53d1\uff1f`,
    section ? `${section} \u4e2d\u7684\u6838\u5fc3\u7ed3\u8bba\u662f\u4ec0\u4e48\uff1f` : ''
  ].filter(Boolean)
}

function ragScore(row) {
  const fields = [row.title, row.symptomSummary, row.causeSummary, row.solutionSummary, row.preventionSummary, row.tags]
  return Math.round((fields.filter(hasText).length / fields.length) * 100)
}

function estimateTokens(value) {
  return Math.max(12, Math.ceil(String(value || '').length / 1.6))
}

function splitText(value) {
  return String(value || '')
    .split(/[\s,，。；;、|]+/)
    .map(item => item.trim())
    .filter(item => item.length >= 2)
}

function sourceText(row) {
  if (row.sourceType === SOURCE_COMPANY_EXCEL) {
    return [row.sourceName, row.sourceSheet, row.sourceRowNumber ? `\u7b2c${row.sourceRowNumber}\u884c` : ''].filter(Boolean).join(' / ')
  }
  return [row.sourceName || zh.issueLedgerTitle, row.issueNo].filter(Boolean).join(' / ')
}

function detailSourceText(row) {
  if (!row) return ''
  const source = sourceText(row)
  const project = row.projectName ? ` / ${row.projectName}` : ''
  return `${source}${project}`
}

function sourceTypeText(type) {
  return type === SOURCE_COMPANY_EXCEL ? zh.companyExcelTitle : zh.issueLedgerTitle
}

function goIssue(row) {
  if (row?.issueId) router.push(`/issues/${row.issueId}`)
}

function valueText(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function hasText(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadRuleOptions(), loadSourceStats()])
})
</script>

<style scoped>
.knowledge-page {
  --kb-blue: #1677ff;
  --kb-text: #1d3354;
  --kb-muted: #667893;
  --kb-line: #e5ebf5;
  --kb-soft: #f6f9fd;
}

.kb-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 26px 28px;
  border: 1px solid var(--kb-line);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(31, 56, 88, 0.08);
}

.source-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  align-items: stretch;
}

.source-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 238px;
  padding: 22px;
  overflow: hidden;
  border: 1px solid var(--kb-line);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 255, 0.94)), #fff;
  box-shadow: 0 18px 40px rgba(31, 56, 88, 0.12);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.source-card:hover {
  transform: translateY(-2px);
  border-color: rgba(22, 119, 255, 0.28);
  box-shadow: 0 22px 54px rgba(31, 56, 88, 0.16);
}

.source-card::after {
  content: '';
  position: absolute;
  right: -48px;
  bottom: -56px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: rgba(22, 119, 255, 0.06);
  pointer-events: none;
}

.source-top,
.detail-card-head,
.qa-result-head,
.chunk-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.source-title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  min-width: 0;
}

.source-icon {
  display: grid;
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #29c3ff, #3b72ff);
  box-shadow: 0 10px 24px rgba(22, 119, 255, 0.28);
  font-size: 23px;
}

.source-card h3 {
  margin: 1px 0 5px;
  color: var(--kb-text);
  font-size: 20px;
  font-weight: 800;
  line-height: 1.25;
}

.source-title-wrap span {
  color: var(--kb-muted);
  font-size: 12px;
  font-weight: 600;
}

.more-btn {
  width: 32px;
  height: 32px;
  color: #8a99ad;
}

.source-desc {
  min-height: 52px;
  margin: 22px 0 20px;
  color: var(--kb-muted);
  line-height: 1.7;
  word-break: break-word;
}

.source-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: auto;
}

.source-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.doc-pill {
  color: #fff;
  background: #ff9f43;
}

.chunk-pill {
  color: #667893;
  background: #eef3f9;
}

.source-pill {
  color: #315272;
  background: #f5f8fc;
}

.source-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.card-action {
  min-width: 74px;
  height: 28px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.card-action.ghost {
  color: #34445f;
  border-color: #e5ebf5;
  background: #fff;
}

.card-action.primary {
  border-color: #2f9bff;
  background: #2f9bff;
}

.card-action.upload {
  border-color: #48c332;
  background: #48c332;
}

.processing-card {
  display: grid;
  gap: 14px;
  padding: 20px 22px;
  border: 1px solid var(--kb-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(31, 56, 88, 0.08);
}

.processing-title {
  color: var(--kb-text);
  font-size: 16px;
  font-weight: 800;
}

.processing-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.process-step {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: #f7faff;
}

.process-step strong {
  color: var(--kb-text);
}

.process-step span {
  color: var(--kb-muted);
  line-height: 1.6;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 18px;
  margin-top: 18px;
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
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
  color: var(--kb-muted);
}

.drawer-title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.35;
}

.detail-stack,
.qa-panel,
.qa-results,
.strategy-list,
.chunk-list {
  display: grid;
  gap: 14px;
}

.detail-card,
.strategy-card,
.chunk-card,
.qa-result {
  padding: 16px;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: #fff;
}

.detail-card-head {
  margin-bottom: 14px;
}

.detail-card-head strong,
.block-title,
.strategy-card strong,
.chunk-card strong,
.qa-result strong {
  color: var(--kb-text);
  font-weight: 800;
}

.detail-card-head p,
.strategy-card p,
.qa-result p {
  margin: 6px 0 0;
  color: var(--kb-muted);
  line-height: 1.6;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.readable-text,
.chunk-card p {
  margin: 0;
  color: #253653;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-summary {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 10px;
  background: var(--kb-soft);
}

.chunk-summary span,
.chunk-meta span,
.chunk-questions span {
  color: var(--kb-muted);
  font-size: 12px;
}

.chunk-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.chunk-questions {
  margin-top: 12px;
}

.chunk-questions ol {
  margin: 6px 0 0;
  padding-left: 18px;
  color: #34445f;
  line-height: 1.7;
}

.strategy-card div {
  margin-top: 8px;
  color: #34445f;
  line-height: 1.6;
}

.strategy-card span {
  display: inline-block;
  min-width: 42px;
  color: var(--kb-muted);
}

.edit-form {
  max-height: min(68vh, 720px);
  overflow: auto;
  padding-right: 6px;
}

@media (max-width: 1280px) {
  .filter-grid,
  .processing-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .kb-hero,
  .pager {
    align-items: stretch;
    flex-direction: column;
  }

  .source-grid,
  .filter-grid,
  .info-grid,
  .processing-steps {
    grid-template-columns: 1fr;
  }

  .wide-item {
    grid-column: span 1;
  }
}
</style>
