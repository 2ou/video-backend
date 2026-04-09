<template>
  <div class="props-card">
    <div class="props-header">
      <div>
        <div class="props-title">节点属性</div>
        <div class="props-subtitle">选中节点后可编辑参数与素材</div>
      </div>
      <el-button v-if="selected" text type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <div v-if="!selected" class="props-empty">
      <div class="empty-mark">+</div>
      <p>请先在画布中点击一个节点</p>
      <p class="props-empty-hint">可双击画布快速添加文本、图片、视频节点</p>
    </div>

    <template v-else>
      <div class="node-chip" :style="{ '--chip-color': nodeMeta?.accent || '#5d7fff' }">
        <span>{{ nodeMeta?.title || selected.type }}</span>
        <small>{{ selected.id }}</small>
      </div>

      <div v-if="selected.type === 'prompt_input'" class="form-section">
        <div class="section-title">文本节点能力</div>
        <el-form label-position="top" size="small">
          <el-form-item label="功能模式">
            <el-select v-model="selectedData.mode" placeholder="请选择">
              <el-option v-for="item in textModes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="文本内容 / 提示词">
            <el-input
              v-model="selectedData.text"
              type="textarea"
              :rows="5"
              placeholder="输入你希望生成的视频描述"
            />
          </el-form-item>

          <el-form-item label="文本模型">
            <el-select v-model="selectedData.text_model" placeholder="请选择模型">
              <el-option v-for="item in textModelsList" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="风格提示(可选)">
            <el-input
              v-model="selectedData.style_hint"
              type="textarea"
              :rows="2"
              placeholder="例如：胶片感、科幻、高速运动镜头"
            />
          </el-form-item>

          <el-button class="wide-btn" @click="polishPrompt">使用 AI 优化提示词</el-button>

          <template v-if="selectedData.mode === 'image_to_prompt'">
            <el-form-item label="反推参考图" style="margin-top: 12px">
              <div class="upload-row">
                <el-upload
                  :show-file-list="false"
                  :http-request="(opt) => uploadAssetTo(opt, 'reverse_prompt_asset_id')"
                >
                  <el-button>上传参考图</el-button>
                </el-upload>
                <span class="asset-value">{{ formatAssetValue(selectedData.reverse_prompt_asset_id) }}</span>
              </div>
            </el-form-item>

            <el-form-item label="反推结果">
              <el-input
                v-model="selectedData.reverse_prompt_result"
                type="textarea"
                :rows="3"
                placeholder="点击下方按钮生成提示词"
              />
            </el-form-item>

            <el-button class="wide-btn" @click="inferPromptFromImage">图片反推提示词</el-button>
          </template>
        </el-form>
      </div>

      <div v-else-if="selected.type === 'input_video'" class="form-section">
        <div class="section-title">图片节点能力</div>
        <el-form label-position="top" size="small">
          <el-form-item label="功能模式">
            <el-select v-model="selectedData.mode" placeholder="请选择">
              <el-option v-for="item in imageModes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="输入图片素材">
            <div class="upload-row">
              <el-upload :show-file-list="false" :http-request="(opt) => uploadAssetTo(opt, 'asset_id')">
                <el-button>上传图片</el-button>
              </el-upload>
              <span class="asset-value">{{ formatAssetValue(selectedData.asset_id) }}</span>
            </div>
          </el-form-item>

          <el-form-item label="图生图提示词">
            <el-input
              v-model="selectedData.image_to_image_prompt"
              type="textarea"
              :rows="3"
              placeholder="描述你希望变化出的图像风格"
            />
          </el-form-item>

          <el-form-item label="图生图强度">
            <el-slider v-model="selectedData.image_to_image_strength" :min="0" :max="1" :step="0.05" />
          </el-form-item>

          <div class="section-title">Google 图像生成</div>

          <el-form-item label="图像模型">
            <el-select v-model="selectedData.generated_image_model" placeholder="请选择模型">
              <el-option
                v-for="item in imageGenerationModels"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="文生图提示词">
            <el-input
              v-model="selectedData.generate_image_prompt"
              type="textarea"
              :rows="3"
              placeholder="输入描述，调用 Google 模型生成参考图"
            />
          </el-form-item>

          <div class="grid-two">
            <el-form-item label="画幅比例">
              <el-select v-model="selectedData.generate_aspect_ratio" placeholder="请选择">
                <el-option label="16:9" value="16:9" />
                <el-option label="9:16" value="9:16" />
                <el-option label="1:1" value="1:1" />
              </el-select>
            </el-form-item>
            <el-form-item label="负向提示词">
              <el-input v-model="selectedData.generate_negative_prompt" placeholder="可选" />
            </el-form-item>
          </div>

          <el-button class="wide-btn" @click="generateImageFromPrompt">生成并回填图片素材</el-button>
        </el-form>
      </div>

      <div v-else-if="selected.type === 'kie_video_task'" class="form-section">
        <div class="section-title">视频节点能力</div>
        <el-form label-position="top" size="small">
          <el-form-item label="生成模式">
            <el-select v-model="selectedData.mode" placeholder="请选择">
              <el-option v-for="item in videoModes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="KIE 视频模型">
            <el-select v-model="selectedData.params.model" placeholder="请选择模型" filterable>
              <el-option v-for="item in videoModels" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <div class="grid-two">
            <el-form-item label="时长(秒)">
              <el-input-number v-model="selectedData.params.duration" :min="1" :max="30" :step="1" controls-position="right" />
            </el-form-item>
            <el-form-item label="帧率(fps)">
              <el-input-number v-model="selectedData.params.fps" :min="8" :max="60" :step="1" controls-position="right" />
            </el-form-item>
          </div>

          <div class="grid-two">
            <el-form-item label="分辨率">
              <el-select v-model="selectedData.params.resolution" placeholder="请选择分辨率">
                <el-option label="720p" value="720p" />
                <el-option label="1080p" value="1080p" />
              </el-select>
            </el-form-item>
            <el-form-item label="画幅比例">
              <el-select v-model="selectedData.params.aspect_ratio" placeholder="请选择比例">
                <el-option label="16:9" value="16:9" />
                <el-option label="9:16" value="9:16" />
                <el-option label="1:1" value="1:1" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="负向提示词">
            <el-input
              v-model="selectedData.params.negative_prompt"
              type="textarea"
              :rows="2"
              placeholder="可选，填入不希望出现的内容"
            />
          </el-form-item>

          <template v-if="selectedData.mode === 'first_frame'">
            <el-form-item label="首帧素材">
              <div class="upload-row">
                <el-upload
                  :show-file-list="false"
                  :http-request="(opt) => uploadAssetTo(opt, 'params.first_frame_asset_id')"
                >
                  <el-button>上传首帧</el-button>
                </el-upload>
                <span class="asset-value">{{ formatAssetValue(selectedData.params.first_frame_asset_id) }}</span>
              </div>
            </el-form-item>
          </template>

          <template v-if="selectedData.mode === 'first_last_frame'">
            <el-form-item label="首帧素材">
              <div class="upload-row">
                <el-upload
                  :show-file-list="false"
                  :http-request="(opt) => uploadAssetTo(opt, 'params.first_frame_asset_id')"
                >
                  <el-button>上传首帧</el-button>
                </el-upload>
                <span class="asset-value">{{ formatAssetValue(selectedData.params.first_frame_asset_id) }}</span>
              </div>
            </el-form-item>

            <el-form-item label="尾帧素材">
              <div class="upload-row">
                <el-upload
                  :show-file-list="false"
                  :http-request="(opt) => uploadAssetTo(opt, 'params.last_frame_asset_id')"
                >
                  <el-button>上传尾帧</el-button>
                </el-upload>
                <span class="asset-value">{{ formatAssetValue(selectedData.params.last_frame_asset_id) }}</span>
              </div>
            </el-form-item>
          </template>

          <template v-if="selectedData.mode === 'nine_grid'">
            <el-form-item label="多图素材列表">
              <div class="upload-row">
                <el-upload
                  :show-file-list="false"
                  :http-request="(opt) => uploadAssetTo(opt, 'params.grid_asset_ids', true)"
                >
                  <el-button>逐个上传图片</el-button>
                </el-upload>
                <el-button text @click="clearGridAssets">清空</el-button>
              </div>
            </el-form-item>
            <div class="grid-assets">
              <span v-for="id in selectedData.params.grid_asset_ids" :key="id" class="asset-tag">#{{ id }}</span>
              <span v-if="!selectedData.params.grid_asset_ids?.length" class="asset-placeholder">暂无素材</span>
            </div>
          </template>
        </el-form>
      </div>

      <div v-else class="form-section">
        <div class="section-title">当前节点为输出节点</div>
        <div class="props-empty-hint">无需额外配置，运行完成后将在运行详情中查看结果。</div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  directUploadAssetApi,
  generateImageApi,
  polishPromptApi,
  reversePromptApi
} from '../../api/assets'
import {
  IMAGE_GENERATION_MODELS,
  IMAGE_NODE_MODES,
  TEXT_MODELS,
  TEXT_NODE_MODES,
  VIDEO_MODELS,
  VIDEO_NODE_MODES,
  ensureNodeDataDefaults,
  getNodeCatalogItem
} from '../../constants/workflow'

const props = defineProps<{ selected: any; projectId: number }>()
defineEmits<{ remove: [] }>()

const textModes = TEXT_NODE_MODES
const imageModes = IMAGE_NODE_MODES
const videoModes = VIDEO_NODE_MODES
const videoModels = VIDEO_MODELS
const textModelsList = TEXT_MODELS
const imageGenerationModels = IMAGE_GENERATION_MODELS

const selectedData = computed(() => props.selected?.data || {})
const nodeMeta = computed(() => getNodeCatalogItem(props.selected?.type || ''))

watch(
  () => props.selected?.id,
  () => {
    if (props.selected) {
      ensureNodeDataDefaults(props.selected)
    }
  },
  { immediate: true }
)

watch(
  () => selectedData.value.mode,
  (mode) => {
    if (props.selected?.type !== 'kie_video_task') return
    if (!selectedData.value.params || typeof selectedData.value.params !== 'object') {
      selectedData.value.params = {}
    }
    selectedData.value.params.generation_mode = mode
  },
  { immediate: true }
)

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
  if (!props.selected) return
  try {
    const res = await directUploadAssetApi(opt.file as File, props.projectId)
    const data = res.data?.data || {}
    const assetId = data.asset_id || data.id

    if (append) {
      const current = getByPath(props.selected.data, path)
      const nextList = Array.isArray(current) ? [...current, assetId] : [assetId]
      setByPath(props.selected.data, path, nextList)
    } else {
      setByPath(props.selected.data, path, assetId)
    }

    ElMessage.success(`上传成功: 素材 #${assetId}`)
    opt.onSuccess?.(res.data)
  } catch (error) {
    ElMessage.error('素材上传失败')
    opt.onError?.(error as Error)
  }
}

const inferPromptFromImage = async () => {
  if (!selectedData.value.reverse_prompt_asset_id) {
    ElMessage.warning('请先上传参考图')
    return
  }
  try {
    const res = await reversePromptApi(
      Number(selectedData.value.reverse_prompt_asset_id),
      selectedData.value.text || ''
    )
    const prompt = res.data?.data?.prompt || ''
    selectedData.value.reverse_prompt_result = prompt
    selectedData.value.text = prompt
    ElMessage.success('图片反推提示词成功')
  } catch (error) {
    ElMessage.error('图片反推提示词失败')
  }
}

const polishPrompt = async () => {
  const text = selectedData.value.text || ''
  if (!text.trim()) {
    ElMessage.warning('请先输入提示词')
    return
  }

  try {
    const res = await polishPromptApi({
      text,
      model: selectedData.value.text_model || 'gpt-5-2',
      style_hint: selectedData.value.style_hint || ''
    })
    const prompt = res.data?.data?.prompt || ''
    if (prompt) {
      selectedData.value.text = prompt
      selectedData.value.text_to_video_prompt = prompt
      ElMessage.success('提示词优化成功')
      return
    }
    ElMessage.warning('未获取到优化结果')
  } catch (error) {
    ElMessage.error('提示词优化失败')
  }
}

const generateImageFromPrompt = async () => {
  const prompt = selectedData.value.generate_image_prompt || selectedData.value.image_to_image_prompt || ''
  if (!prompt.trim()) {
    ElMessage.warning('请输入文生图提示词')
    return
  }

  try {
    const res = await generateImageApi(
      {
        model: selectedData.value.generated_image_model || 'google/imagen4-fast',
        prompt,
        negative_prompt: selectedData.value.generate_negative_prompt || '',
        aspect_ratio: selectedData.value.generate_aspect_ratio || '16:9'
      },
      props.projectId
    )
    const assetId = res.data?.data?.asset_id
    if (assetId) {
      selectedData.value.asset_id = Number(assetId)
      ElMessage.success(`图像生成成功: 素材 #${assetId}`)
      return
    }
    ElMessage.warning('图像生成完成但未返回素材编号')
  } catch (error) {
    ElMessage.error('图像生成失败')
  }
}

const clearGridAssets = () => {
  if (!selectedData.value?.params) return
  selectedData.value.params.grid_asset_ids = []
}

const formatAssetValue = (value: any) => {
  if (value === null || value === undefined || value === '') return '未上传'
  return `#${value}`
}
</script>

<style scoped>
.props-card {
  border-radius: 18px;
  border: 1px solid rgba(101, 117, 161, 0.42);
  background: linear-gradient(160deg, rgba(17, 22, 31, 0.95), rgba(11, 14, 21, 0.92));
  color: #eff3ff;
  padding: 14px;
  height: 100%;
  overflow: auto;
}

.props-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.props-title {
  font-size: 14px;
  font-weight: 700;
}

.props-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(199, 209, 248, 0.74);
}

.props-empty {
  margin-top: 20px;
  border: 1px dashed rgba(132, 148, 201, 0.32);
  border-radius: 14px;
  padding: 18px 14px;
  text-align: center;
  color: rgba(215, 223, 255, 0.9);
}

.empty-mark {
  width: 34px;
  height: 34px;
  line-height: 34px;
  margin: 0 auto 10px;
  border-radius: 50%;
  border: 1px solid rgba(151, 168, 225, 0.45);
}

.props-empty p {
  margin: 6px 0 0;
}

.props-empty-hint {
  font-size: 12px;
  color: rgba(196, 206, 247, 0.72);
}

.node-chip {
  margin-top: 14px;
  border-radius: 12px;
  border: 1px solid color-mix(in oklab, var(--chip-color) 45%, #7f91c4 55%);
  background: color-mix(in oklab, var(--chip-color) 18%, #141b2a 82%);
  padding: 9px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-chip span {
  font-size: 13px;
  font-weight: 700;
}

.node-chip small {
  color: rgba(210, 218, 248, 0.78);
  font-size: 11px;
}

.form-section {
  margin-top: 14px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  color: rgba(236, 242, 255, 0.95);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.asset-value {
  font-size: 12px;
  color: rgba(198, 209, 244, 0.8);
}

.wide-btn {
  width: 100%;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.grid-assets {
  min-height: 32px;
  border: 1px dashed rgba(137, 153, 202, 0.38);
  border-radius: 10px;
  padding: 8px;
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

:deep(.el-form-item) {
  margin-bottom: 12px;
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

:deep(.el-select .el-select__placeholder),
:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: rgba(181, 194, 235, 0.62);
}

@media (max-width: 1200px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
