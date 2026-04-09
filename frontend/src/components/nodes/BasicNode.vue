<template>
  <div class="workflow-node" :class="{ 'is-selected': selected }" :style="{ '--accent-color': meta?.accent || '#4f7cff' }">
    <Handle type="target" :position="Position.Left" class="workflow-handle" />

    <div class="node-header">
      <span class="node-icon">{{ meta?.icon || 'N' }}</span>
      <div class="node-title-wrap">
        <div class="node-title">{{ data?.label || meta?.title || type }}</div>
        <div class="node-subtitle">{{ subtitle }}</div>
      </div>
      <el-button v-if="selected" text type="danger" size="small" class="delete-btn" @click="removeSelf">
        删除
      </el-button>
    </div>

    <div class="node-body nodrag nopan nowheel">
      <template v-if="!selected">
        <p class="node-highlight">{{ subtitle }}</p>
        <p class="node-text">{{ summaryText }}</p>
      </template>

      <template v-else-if="type === 'prompt_input'">
        <el-form label-position="top" size="small" class="node-form nodrag nopan nowheel">
          <el-form-item label="模式">
            <el-select v-model="data.mode">
              <el-option v-for="item in textModes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="提示词">
            <el-input v-model="data.text" type="textarea" :rows="3" placeholder="输入视频提示词" />
          </el-form-item>

          <div class="grid-two">
            <el-form-item label="文本模型">
              <el-select v-model="data.text_model">
                <el-option v-for="item in textModelList" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="风格提示">
              <el-input v-model="data.style_hint" placeholder="可选" />
            </el-form-item>
          </div>

          <el-button class="wide-btn" @click="polishPrompt">AI 优化提示词</el-button>

          <template v-if="data.mode === 'image_to_prompt'">
            <el-form-item label="反推参考图" style="margin-top: 8px">
              <div class="upload-row">
                <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'reverse_prompt_asset_id')">
                  <el-button>上传</el-button>
                </el-upload>
                <span class="asset-value">{{ formatAssetValue(data.reverse_prompt_asset_id) }}</span>
              </div>
            </el-form-item>

            <el-form-item label="反推结果">
              <el-input v-model="data.reverse_prompt_result" type="textarea" :rows="2" />
            </el-form-item>

            <el-button class="wide-btn" @click="inferPromptFromImage">图片反推提示词</el-button>
          </template>
        </el-form>
      </template>

      <template v-else-if="type === 'input_video'">
        <el-form label-position="top" size="small" class="node-form nodrag nopan nowheel">
          <el-form-item label="输入图片素材">
            <div class="upload-row">
              <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'asset_id')">
                <el-button>上传图片</el-button>
              </el-upload>
              <span class="asset-value">{{ formatAssetValue(data.asset_id) }}</span>
            </div>
          </el-form-item>

          <el-form-item label="图生图提示词">
            <el-input v-model="data.image_to_image_prompt" type="textarea" :rows="2" />
          </el-form-item>

          <el-form-item label="Google 模型">
            <el-select v-model="data.generated_image_model">
              <el-option
                v-for="item in imageGenerationModels"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="文生图提示词">
            <el-input v-model="data.generate_image_prompt" type="textarea" :rows="2" />
          </el-form-item>

          <el-button class="wide-btn" @click="generateImageFromPrompt">生成并回填素材</el-button>
        </el-form>
      </template>

      <template v-else-if="type === 'kie_video_task'">
        <el-form label-position="top" size="small" class="node-form nodrag nopan nowheel">
          <el-form-item label="生成模式">
            <el-select v-model="data.mode">
              <el-option v-for="item in videoModes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="视频模型">
            <el-select v-model="data.params.model" filterable>
              <el-option v-for="item in videoModels" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <div class="grid-two">
            <el-form-item label="时长">
              <el-input-number v-model="data.params.duration" :min="1" :max="30" :step="1" controls-position="right" />
            </el-form-item>
            <el-form-item label="帧率">
              <el-input-number v-model="data.params.fps" :min="8" :max="60" :step="1" controls-position="right" />
            </el-form-item>
          </div>

          <div class="grid-two">
            <el-form-item label="分辨率">
              <el-select v-model="data.params.resolution">
                <el-option label="720p" value="720p" />
                <el-option label="1080p" value="1080p" />
              </el-select>
            </el-form-item>
            <el-form-item label="比例">
              <el-select v-model="data.params.aspect_ratio">
                <el-option label="16:9" value="16:9" />
                <el-option label="9:16" value="9:16" />
                <el-option label="1:1" value="1:1" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="负向提示词">
            <el-input v-model="data.params.negative_prompt" type="textarea" :rows="2" />
          </el-form-item>

          <template v-if="data.mode === 'first_frame'">
            <el-form-item label="首帧素材">
              <div class="upload-row">
                <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'params.first_frame_asset_id')">
                  <el-button>上传首帧</el-button>
                </el-upload>
                <span class="asset-value">{{ formatAssetValue(data.params.first_frame_asset_id) }}</span>
              </div>
            </el-form-item>
          </template>

          <template v-if="data.mode === 'first_last_frame'">
            <div class="grid-two">
              <el-form-item label="首帧素材">
                <div class="upload-row">
                  <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'params.first_frame_asset_id')">
                    <el-button>上传首帧</el-button>
                  </el-upload>
                  <span class="asset-value">{{ formatAssetValue(data.params.first_frame_asset_id) }}</span>
                </div>
              </el-form-item>
              <el-form-item label="尾帧素材">
                <div class="upload-row">
                  <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'params.last_frame_asset_id')">
                    <el-button>上传尾帧</el-button>
                  </el-upload>
                  <span class="asset-value">{{ formatAssetValue(data.params.last_frame_asset_id) }}</span>
                </div>
              </el-form-item>
            </div>
          </template>

          <template v-if="data.mode === 'nine_grid'">
            <el-form-item label="多图素材">
              <div class="upload-row">
                <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'params.grid_asset_ids', true)">
                  <el-button>添加图片</el-button>
                </el-upload>
                <el-button text @click="clearGridAssets">清空</el-button>
              </div>
            </el-form-item>
            <div class="grid-assets">
              <span v-for="id in data.params.grid_asset_ids" :key="id" class="asset-tag">#{{ id }}</span>
              <span v-if="!data.params.grid_asset_ids?.length" class="asset-placeholder">暂无素材</span>
            </div>
          </template>
        </el-form>
      </template>

      <template v-else>
        <p class="node-highlight">输出节点</p>
        <p class="node-text">运行完成后在详情页查看结果。</p>
      </template>
    </div>

    <Handle type="source" :position="Position.Right" class="workflow-handle" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { useRoute } from 'vue-router'
import { generateImageApi, polishPromptApi, reversePromptApi, uploadAssetApi } from '../../api/assets'
import {
  IMAGE_GENERATION_MODELS,
  TEXT_MODELS,
  TEXT_NODE_MODES,
  VIDEO_MODELS,
  VIDEO_NODE_MODES,
  createDefaultNodeData,
  getNodeCatalogItem,
  type WorkflowNodeType
} from '../../constants/workflow'

const props = defineProps<{
  id: string
  data: any
  type: string
  selected?: boolean
}>()

const { removeNodes } = useVueFlow()
const route = useRoute()

const textModes = TEXT_NODE_MODES
const videoModes = VIDEO_NODE_MODES
const videoModels = VIDEO_MODELS
const textModelList = TEXT_MODELS
const imageGenerationModels = IMAGE_GENERATION_MODELS

const meta = computed(() => getNodeCatalogItem(props.type))
const projectId = computed(() => Number(route.params.id || 0))

const textModeLabel = computed(() => {
  const mode = props.data?.mode
  if (mode === 'text_to_video') return '文生视频提示词'
  if (mode === 'image_to_prompt') return '图片反推提示词'
  return '自己编写'
})

const videoModeLabel = computed(() => {
  const mode = props.data?.mode
  if (mode === 'first_last_frame') return '首尾帧生视频'
  if (mode === 'nine_grid') return '多图生视频'
  return '首帧生视频'
})

const subtitle = computed(() => {
  if (props.type === 'prompt_input') return textModeLabel.value
  if (props.type === 'kie_video_task') return videoModeLabel.value
  return meta.value?.subtitle || props.type
})

const summaryText = computed(() => {
  if (props.type === 'prompt_input') {
    return (props.data?.text || '').slice(0, 60) || '未填写提示词'
  }
  if (props.type === 'input_video') {
    return props.data?.asset_id ? `素材 #${props.data.asset_id}` : '未上传图片素材'
  }
  if (props.type === 'kie_video_task') {
    return `模型 ${props.data?.params?.model || 'grok-imagine/text-to-video'}`
  }
  return '等待上游节点处理完成'
})

const ensureDataDefaults = () => {
  if (!props.data || typeof props.data !== 'object') return
  const defaults = createDefaultNodeData(props.type as WorkflowNodeType)
  mergeDefaults(props.data, defaults)
}

watch(
  () => props.type,
  () => ensureDataDefaults(),
  { immediate: true }
)

watch(
  () => props.data?.mode,
  (mode) => {
    if (props.type !== 'kie_video_task') return
    if (!props.data.params || typeof props.data.params !== 'object') {
      props.data.params = {}
    }
    props.data.params.generation_mode = mode
  },
  { immediate: true }
)

const mergeDefaults = (target: Record<string, any>, defaults: Record<string, any>) => {
  Object.entries(defaults).forEach(([key, value]) => {
    if (target[key] === undefined) {
      target[key] = Array.isArray(value)
        ? [...value]
        : value && typeof value === 'object'
        ? { ...(value as Record<string, any>) }
        : value
      return
    }
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      target[key] &&
      typeof target[key] === 'object' &&
      !Array.isArray(target[key])
    ) {
      mergeDefaults(target[key], value as Record<string, any>)
    }
  })
}

const removeSelf = () => {
  removeNodes([props.id])
}

const setByPath = (source: Record<string, any>, path: string, value: any) => {
  const keys = path.split('.')
  let current: Record<string, any> = source
  keys.forEach((key, index) => {
    if (index === keys.length - 1) {
      current[key] = value
      return
    }
    if (!current[key] || typeof current[key] !== 'object') {
      current[key] = {}
    }
    current = current[key]
  })
}

const getByPath = (source: Record<string, any>, path: string) => {
  return path.split('.').reduce((acc: any, key) => (acc && key in acc ? acc[key] : undefined), source)
}

const uploadAssetTo = async (opt: UploadRequestOptions, path: string, append = false) => {
  try {
    const res = await uploadAssetApi(opt.file as File, projectId.value)
    const assetId = res.data.data.id

    if (append) {
      const current = getByPath(props.data, path)
      const nextList = Array.isArray(current) ? [...current, assetId] : [assetId]
      setByPath(props.data, path, nextList)
    } else {
      setByPath(props.data, path, assetId)
    }

    ElMessage.success(`上传成功: #${assetId}`)
    opt.onSuccess?.(res.data)
  } catch (error) {
    ElMessage.error('素材上传失败')
    opt.onError?.(error as Error)
  }
}

const inferPromptFromImage = async () => {
  if (!props.data.reverse_prompt_asset_id) {
    ElMessage.warning('请先上传参考图')
    return
  }
  try {
    const res = await reversePromptApi(Number(props.data.reverse_prompt_asset_id), props.data.text || '')
    const prompt = res.data?.data?.prompt || ''
    props.data.reverse_prompt_result = prompt
    props.data.text = prompt
    ElMessage.success('反推成功')
  } catch (error) {
    ElMessage.error('反推失败')
  }
}

const polishPrompt = async () => {
  const text = props.data.text || ''
  if (!text.trim()) {
    ElMessage.warning('请先输入提示词')
    return
  }

  try {
    const res = await polishPromptApi({
      text,
      model: props.data.text_model || 'gpt-5-2',
      style_hint: props.data.style_hint || ''
    })
    const prompt = res.data?.data?.prompt || ''
    if (prompt) {
      props.data.text = prompt
      props.data.text_to_video_prompt = prompt
      ElMessage.success('优化成功')
      return
    }
    ElMessage.warning('未返回优化结果')
  } catch (error) {
    ElMessage.error('优化失败')
  }
}

const generateImageFromPrompt = async () => {
  const prompt = props.data.generate_image_prompt || props.data.image_to_image_prompt || ''
  if (!prompt.trim()) {
    ElMessage.warning('请输入文生图提示词')
    return
  }

  try {
    const res = await generateImageApi(
      {
        model: props.data.generated_image_model || 'google/imagen4-fast',
        prompt,
        negative_prompt: props.data.generate_negative_prompt || '',
        aspect_ratio: props.data.generate_aspect_ratio || '16:9'
      },
      projectId.value
    )

    const assetId = res.data?.data?.asset_id
    if (assetId) {
      props.data.asset_id = Number(assetId)
      ElMessage.success(`图像生成成功: #${assetId}`)
      return
    }
    ElMessage.warning('已生成但未返回素材编号')
  } catch (error) {
    ElMessage.error('图像生成失败')
  }
}

const clearGridAssets = () => {
  if (!props.data?.params) return
  props.data.params.grid_asset_ids = []
}

const formatAssetValue = (value: any) => {
  if (value === null || value === undefined || value === '') return '未上传'
  return `#${value}`
}
</script>

<style scoped>
.workflow-node {
  position: relative;
  width: 280px;
  border-radius: 14px;
  border: 1px solid color-mix(in oklab, var(--accent-color) 40%, #ffffff 18%);
  background: linear-gradient(160deg, rgba(20, 24, 31, 0.96), rgba(10, 13, 20, 0.94));
  box-shadow: 0 18px 45px rgba(5, 7, 12, 0.45);
  color: #e8ecff;
  padding: 12px;
  transition: width 0.2s ease, box-shadow 0.2s ease;
}

.workflow-node.is-selected {
  width: 360px;
  box-shadow: 0 20px 55px rgba(4, 8, 18, 0.58);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in oklab, var(--accent-color) 35%, #0e1320 65%);
  color: #fff;
  font-weight: 700;
  font-size: 13px;
}

.node-title-wrap {
  min-width: 0;
  flex: 1;
}

.node-title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.node-subtitle {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(207, 214, 255, 0.78);
}

.delete-btn {
  margin-left: auto;
}

.node-body {
  margin-top: 10px;
  border-radius: 10px;
  border: 1px solid rgba(145, 157, 214, 0.2);
  background: rgba(19, 24, 33, 0.72);
  padding: 10px;
}

.node-highlight {
  font-size: 11px;
  font-weight: 700;
  color: color-mix(in oklab, var(--accent-color) 72%, #ffffff 28%);
  margin: 0 0 6px;
}

.node-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: rgba(231, 235, 255, 0.88);
}

.node-form {
  max-height: 360px;
  overflow: auto;
  padding-right: 2px;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.asset-value {
  font-size: 12px;
  color: rgba(198, 209, 244, 0.8);
}

.wide-btn {
  width: 100%;
}

.grid-assets {
  min-height: 30px;
  border: 1px dashed rgba(137, 153, 202, 0.38);
  border-radius: 8px;
  padding: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.asset-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(75, 103, 188, 0.35);
  color: #e5ecff;
  font-size: 12px;
}

.asset-placeholder {
  font-size: 12px;
  color: rgba(194, 203, 240, 0.72);
}

.workflow-handle {
  width: 10px;
  height: 10px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: color-mix(in oklab, var(--accent-color) 70%, #f2f6ff 30%);
}

:deep(.el-form-item) {
  margin-bottom: 10px;
}

:deep(.el-form-item__label) {
  color: rgba(227, 236, 255, 0.92);
  padding-bottom: 4px;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper),
:deep(.el-input-number),
:deep(.el-input-number__wrapper) {
  background: rgba(14, 20, 33, 0.9);
  box-shadow: 0 0 0 1px rgba(109, 124, 168, 0.45) inset;
  color: #ecf2ff;
}

@media (max-width: 900px) {
  .workflow-node,
  .workflow-node.is-selected {
    width: 300px;
  }

  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
