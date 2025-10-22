<template>
  <div class="file-upload-container">
    <!-- 文件上传区域 -->
    <div 
      class="file-upload-area"
      :class="{ 'drag-over': isDragOver, 'disabled': disabled }"
      @click="triggerFileInput"
      @dragover.prevent="handleDragOver"
      @dragleave.prevent="handleDragLeave"
      @drop.prevent="handleDrop"
    >
      <input
        ref="fileInput"
        type="file"
        :accept="allowedTypes"
        multiple
        @change="handleFileSelect"
        style="display: none"
      />
      
      <div class="upload-content">
        <i class="bi bi-cloud-upload upload-icon"></i>
        <div class="upload-text">
          <span v-if="!isDragOver">点击或拖拽上传CSV文件</span>
          <span v-else>释放文件以上传</span>
        </div>
        <div class="upload-hint">
          支持 {{ allowedTypesText }} 格式，最大 {{ maxSizeText }}
        </div>
      </div>
    </div>
    
    <!-- 已上传文件列表 -->
    <div v-if="uploadedFiles.length > 0" class="uploaded-files">
      <div class="files-header">
        <span class="files-title">已上传文件</span>
        <button class="clear-all-btn" @click="clearAllFiles" v-if="uploadedFiles.length > 0">
          <i class="bi bi-trash"></i>
          清空
        </button>
      </div>
      
      <div class="files-list">
        <div 
          v-for="(file, index) in uploadedFiles" 
          :key="file.id || index"
          class="file-item"
        >
          <div class="file-info">
            <div class="file-icon">
              <i :class="getFileIcon(file.type)"></i>
            </div>
            <div class="file-details">
              <div class="file-name" :title="file.originalFilename">
                {{ file.originalFilename }}
              </div>
              <div class="file-meta">
                <span class="file-size">{{ formatFileSize(file.fileSize) }}</span>
                <span class="file-status" :class="file.status">
                  {{ getStatusText(file.status) }}
                </span>
              </div>
            </div>
          </div>
          
          <div class="file-actions">
            <button 
              class="action-btn preview-btn" 
              @click="previewFile(file)"
              title="预览"
            >
              <i class="bi bi-eye"></i>
            </button>
            <button 
              class="action-btn delete-btn" 
              @click="removeFile(index)"
              title="删除"
            >
              <i class="bi bi-trash"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 上传进度 -->
    <div v-if="uploading" class="upload-progress">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div>
      </div>
      <span class="progress-text">上传中... {{ uploadProgress }}%</span>
    </div>
  </div>
</template>

<script>
import { ref, computed, watch } from 'vue'

export default {
  name: 'FileUpload',
  props: {
    agentId: {
      type: Number,
      required: true
    },
    sessionId: {
      type: String,
      required: true
    },
    maxFileSize: {
      type: Number,
      default: 10485760 // 10MB
    },
    allowedTypes: {
      type: String,
      default: 'csv,xlsx'
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  emits: ['files-uploaded', 'files-removed'],
  setup(props, { emit }) {
    const fileInput = ref(null)
    const uploadedFiles = ref([])
    const isDragOver = ref(false)
    const uploading = ref(false)
    const uploadProgress = ref(0)
    
    const allowedTypesText = computed(() => {
      return props.allowedTypes.split(',').map(type => type.toUpperCase()).join(', ')
    })
    
    const maxSizeText = computed(() => {
      const sizeInMB = props.maxFileSize / (1024 * 1024)
      return `${sizeInMB}MB`
    })
    
    const triggerFileInput = () => {
      if (!props.disabled) {
        fileInput.value?.click()
      }
    }
    
    const handleDragOver = (e) => {
      if (!props.disabled) {
        e.preventDefault()
        isDragOver.value = true
      }
    }
    
    const handleDragLeave = (e) => {
      e.preventDefault()
      isDragOver.value = false
    }
    
    const handleDrop = (e) => {
      if (props.disabled) return
      
      e.preventDefault()
      isDragOver.value = false
      
      const files = Array.from(e.dataTransfer.files)
      handleFiles(files)
    }
    
    const handleFileSelect = (e) => {
      const files = Array.from(e.target.files)
      handleFiles(files)
      // 清空input值，允许重复选择同一文件
      e.target.value = ''
    }
    
    const handleFiles = async (files) => {
      if (files.length === 0) return
      
      // 验证文件
      const validFiles = files.filter(file => {
        const isValidType = props.allowedTypes.split(',').some(type => 
          file.name.toLowerCase().endsWith(type.toLowerCase())
        )
        const isValidSize = file.size <= props.maxFileSize
        
        if (!isValidType) {
          alert(`文件 ${file.name} 类型不支持，允许的类型：${props.allowedTypes}`)
        }
        if (!isValidSize) {
          alert(`文件 ${file.name} 大小超过限制，最大允许 ${maxSizeText.value}`)
        }
        
        return isValidType && isValidSize
      })
      
      if (validFiles.length === 0) return
      
      // 开始上传
      uploading.value = true
      uploadProgress.value = 0
      
      try {
        for (const file of validFiles) {
          const formData = new FormData()
          formData.append('file', file)
          formData.append('agentId', props.agentId)
          formData.append('sessionId', props.sessionId)
          
          const response = await fetch('/api/csv/upload', {
            method: 'POST',
            body: formData
          })
          
          const result = await response.json()
          
          if (result.success) {
            uploadedFiles.value.push({
              ...result.data,
              status: 'ACTIVE'
            })
          } else {
            console.error('文件上传失败:', result.message)
            alert(`文件 ${file.name} 上传失败：${result.message}`)
          }
          
          uploadProgress.value = Math.round(((validFiles.indexOf(file) + 1) / validFiles.length) * 100)
        }
        
        emit('files-uploaded', uploadedFiles.value)
        
      } catch (error) {
        console.error('文件上传失败:', error)
        alert('文件上传失败，请重试')
      } finally {
        uploading.value = false
        uploadProgress.value = 0
      }
    }
    
    const removeFile = async (index) => {
      const file = uploadedFiles.value[index]
      
      try {
        const response = await fetch(`/api/csv/${file.id}`, {
          method: 'DELETE'
        })
        
        const result = await response.json()
        
        if (result.success) {
          uploadedFiles.value.splice(index, 1)
          emit('files-removed', uploadedFiles.value)
        } else {
          alert('删除文件失败：' + result.message)
        }
      } catch (error) {
        console.error('删除文件失败:', error)
        alert('删除文件失败，请重试')
      }
    }
    
    const clearAllFiles = async () => {
      if (confirm('确定要清空所有文件吗？')) {
        try {
          for (const file of uploadedFiles.value) {
            await fetch(`/api/csv/${file.id}`, { method: 'DELETE' })
          }
          uploadedFiles.value = []
          emit('files-removed', [])
        } catch (error) {
          console.error('清空文件失败:', error)
          alert('清空文件失败，请重试')
        }
      }
    }
    
    const previewFile = (file) => {
      // TODO: 实现文件预览功能
      console.log('预览文件:', file)
    }
    
    const getFileIcon = (fileType) => {
      switch (fileType.toLowerCase()) {
        case 'csv':
          return 'bi bi-filetype-csv'
        case 'xlsx':
        case 'xls':
          return 'bi bi-filetype-xlsx'
        default:
          return 'bi bi-file-earmark'
      }
    }
    
    const getStatusText = (status) => {
      switch (status) {
        case 'ACTIVE':
          return '已上传'
        case 'PROCESSING':
          return '处理中'
        case 'ERROR':
          return '错误'
        default:
          return '未知'
      }
    }
    
    const formatFileSize = (bytes) => {
      if (bytes === 0) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }
    
    // 监听会话变化，清空文件列表
    watch(() => props.sessionId, () => {
      uploadedFiles.value = []
    })
    
    return {
      fileInput,
      uploadedFiles,
      isDragOver,
      uploading,
      uploadProgress,
      allowedTypesText,
      maxSizeText,
      triggerFileInput,
      handleDragOver,
      handleDragLeave,
      handleDrop,
      handleFileSelect,
      removeFile,
      clearAllFiles,
      previewFile,
      getFileIcon,
      getStatusText,
      formatFileSize
    }
  }
}
</script>

<style scoped>
.file-upload-container {
  margin-bottom: 16px;
}

.file-upload-area {
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #fafafa;
}

.file-upload-area:hover {
  border-color: #1890ff;
  background: #f0f8ff;
}

.file-upload-area.drag-over {
  border-color: #1890ff;
  background: #e6f7ff;
}

.file-upload-area.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 32px;
  color: #1890ff;
}

.upload-text {
  font-size: 16px;
  color: #333;
  font-weight: 500;
}

.upload-hint {
  font-size: 12px;
  color: #999;
}

.uploaded-files {
  margin-top: 16px;
}

.files-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.files-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.clear-all-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  background: #ff4d4f;
  color: white;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.clear-all-btn:hover {
  background: #ff7875;
}

.files-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  background: white;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.file-icon {
  font-size: 20px;
  color: #1890ff;
}

.file-details {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.file-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.file-size {
  color: #666;
}

.file-status {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
}

.file-status.ACTIVE {
  background: #f6ffed;
  color: #52c41a;
}

.file-status.PROCESSING {
  background: #fff7e6;
  color: #fa8c16;
}

.file-status.ERROR {
  background: #fff2f0;
  color: #ff4d4f;
}

.file-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.preview-btn {
  background: #f0f8ff;
  color: #1890ff;
}

.preview-btn:hover {
  background: #e6f7ff;
}

.delete-btn {
  background: #fff2f0;
  color: #ff4d4f;
}

.delete-btn:hover {
  background: #ffebe6;
}

.upload-progress {
  margin-top: 12px;
  padding: 8px 12px;
  background: #f0f8ff;
  border-radius: 6px;
}

.progress-bar {
  width: 100%;
  height: 4px;
  background: #e8e8e8;
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 4px;
}

.progress-fill {
  height: 100%;
  background: #1890ff;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: #666;
}
</style>
