<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type UploadFile } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import ExecutionWorkbench from '@/components/observability/ExecutionWorkbench.vue'
import { documents, ingestionJob, uploadDocument } from '@/api/rag'
import { errorMessage } from '@/api/http'
import type { DocumentSummary, IngestionJob } from '@/types/rag'
import { formatTime, statusLabel, statusType } from '@/utils'
import { uploadAction } from '@/documents/uploadResult'
import { ingestionElapsed, ingestionExecutionEvents } from '@/ingestion/executionReport'

const rows = ref<DocumentSummary[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dialog = ref(false)
const file = ref<File | null>(null)
const category = ref('公司制度')
const uploading = ref(false)
const job = ref<IngestionJob | null>(null)
const jobDrawer = ref(false)
const router = useRouter()
const ingestionEvents = computed(() => job.value ? ingestionExecutionEvents(job.value) : [])
const ingestionTotalMs = computed(() => job.value ? ingestionElapsed(job.value) : null)
let poller: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try {
    const result = await documents(page.value - 1, 20)
    rows.value = result.items
    total.value = result.total
  } finally { loading.value = false }
}

function choose(upload: UploadFile) { file.value = upload.raw ?? null }

async function submit() {
  if (!file.value) return ElMessage.warning('请选择 Word、PDF 或 Excel 文件')
  uploading.value = true
  try {
    const result = await uploadDocument(file.value, category.value)
    dialog.value = false
    file.value = null
    if (uploadAction(result) === 'OPEN_EXISTING') {
      ElMessage.warning(result.message)
      await router.push(`/documents/${result.documentId}`)
      return
    }
    jobDrawer.value = true
    await watchJob(result.jobId!)
    ElMessage.success(result.message)
    await load()
  } catch (error) { ElMessage.error(errorMessage(error, '上传失败')) } finally { uploading.value = false }
}

async function watchJob(jobId: number) {
  if (poller) clearInterval(poller)
  const refresh = async () => {
    job.value = await ingestionJob(jobId)
    if (['SUCCEEDED', 'FAILED'].includes(job.value.status) && poller) {
      clearInterval(poller); poller = null; await load()
    }
  }
  await refresh()
  poller = setInterval(refresh, 1500)
}

onMounted(load)
onBeforeUnmount(() => { if (poller) clearInterval(poller) })
</script>

<template>
  <section>
    <PageHeading eyebrow="KNOWLEDGE INGESTION" title="文档与入库" description="上传原始文件，查看解析进度，并检查最终生成的知识切片。">
      <el-button @click="load">刷新</el-button><el-button type="primary" @click="dialog = true">上传文件</el-button>
    </PageHeading>
    <article class="panel table-panel">
      <el-table :data="rows" v-loading="loading" @row-click="(row: DocumentSummary) => router.push(`/documents/${row.id}`)">
        <el-table-column prop="name" label="文档" min-width="280"><template #default="{ row }"><div class="doc-cell"><b>{{ row.name }}</b><small>{{ row.category || '未分类' }}</small></div></template></el-table-column>
        <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="sourceType" label="来源" width="110"><template #default>人工上传</template></el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="190"><template #default="{ row }">{{ formatTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column width="90" align="right"><template #default><span class="table-link">详情 →</span></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next, total" @current-change="load" />
    </article>

    <el-dialog v-model="dialog" title="上传知识文件" width="520px">
      <el-form label-position="top">
        <el-form-item label="文件分类"><el-input v-model="category" placeholder="例如：公司制度、经营规划" /></el-form-item>
        <el-form-item label="原始文件">
          <el-upload drag :auto-upload="false" :limit="1" accept=".docx,.pdf,.xlsx,.xls" :on-change="choose"><div class="upload-copy"><b>拖拽文件到这里，或点击选择</b><span>支持 DOCX、PDF、XLSX、XLS，单文件不超过 50 MB</span></div></el-upload>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="uploading" @click="submit">开始入库</el-button></template>
    </el-dialog>

    <el-drawer v-model="jobDrawer" title="入库任务" size="92%">
      <div v-if="job" class="job-summary"><el-tag :type="statusType(job.status)">{{ statusLabel(job.status) }}</el-tag><span>当前阶段：{{ job.currentStage || '等待中' }}</span></div>
      <ExecutionWorkbench v-if="job" mode="ingestion" :events="ingestionEvents" :total-ms="ingestionTotalMs" :result-count="job.stages.at(-1)?.itemCount ?? null" />
      <el-alert v-if="job?.errorMessage" :title="job.errorMessage" type="error" :closable="false" />
    </el-drawer>
  </section>
</template>
