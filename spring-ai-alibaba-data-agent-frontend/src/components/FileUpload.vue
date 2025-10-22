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
              class="action-btn download-btn" 
              @click="downloadFile(file)"
              title="下载"
            >
              <i class="bi bi-download"></i>
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
import { ref, computed, watch, onMounted } from 'vue'

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
    console.log('=== FileUpload组件初始化 ===')
    console.log('props:', props)
    
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
      console.log('=== 文件选择调试 ===')
      console.log('选择的文件数量:', files.length)
      console.log('文件列表:', files)
      
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
            console.log('后端返回的数据:', result.data)
            const fileData = {
              ...result.data,
              status: 'ACTIVE',
              // 确保文件大小正确显示
              fileSize: result.data.fileSize || file.size,
              originalFilename: result.data.originalFilename || file.name,
              fileType: result.data.fileType || getFileExtension(file.name)
            }
            console.log('处理后的文件数据:', fileData)
            uploadedFiles.value.push(fileData)
          } else {
            console.error('文件上传失败:', result.message)
            alert(`文件 ${file.name} 上传失败：${result.message}`)
          }
          
          uploadProgress.value = Math.round(((validFiles.indexOf(file) + 1) / validFiles.length) * 100)
        }
        
        console.log('=== FileUpload组件调试 ===')
        console.log('准备触发files-uploaded事件')
        console.log('uploadedFiles.value:', uploadedFiles.value)
        emit('files-uploaded', uploadedFiles.value)
        console.log('files-uploaded事件已触发')
        
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
      console.log('准备删除文件:', file)
      console.log('文件ID:', file.id)
      
      if (!file.id) {
        alert('文件ID不存在，无法删除')
        return
      }
      
      try {
        console.log('发送删除请求到:', `/api/csv/${file.id}`)
        const response = await fetch(`/api/csv/${file.id}`, {
          method: 'DELETE'
        })
        
        console.log('删除响应状态:', response.status)
        const result = await response.json()
        console.log('删除响应数据:', result)
        
        if (result.success) {
          uploadedFiles.value.splice(index, 1)
          emit('files-removed', uploadedFiles.value)
          console.log('文件删除成功，更新文件列表')
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
      console.log('预览文件:', file)
      
      // 检查文件类型，支持CSV和Excel文件预览
      const fileType = file.fileType?.toLowerCase() || getFileExtension(file.originalFilename)?.toLowerCase()
      
      if (fileType === 'csv') {
        // 对于CSV文件，显示表格预览
        if (file.id) {
          showCsvPreview(file)
        } else {
          alert('文件ID不存在，无法预览')
        }
      } else if (fileType === 'xlsx' || fileType === 'xls') {
        // 对于Excel文件，使用下载接口
        if (file.id) {
          const downloadUrl = `/api/csv/${file.id}/download`
          console.log('下载Excel文件:', downloadUrl)
          window.open(downloadUrl, '_blank')
        } else {
          alert('文件ID不存在，无法下载')
        }
      } else {
        alert('暂不支持此文件类型的预览')
      }
    }
    
    const showCsvPreview = async (file) => {
      try {
        console.log('获取CSV预览数据:', file.id)
        const response = await fetch(`/api/csv/${file.id}/preview`)
        const data = await response.json()
        
        if (data.success) {
          // 显示预览模态框
          showPreviewModal(data)
        } else {
          alert('预览失败：' + data.message)
        }
      } catch (error) {
        console.error('预览CSV文件失败:', error)
        alert('预览失败：' + error.message)
      }
    }
    
    const showPreviewModal = (data) => {
      // 创建预览模态框
      const modal = document.createElement('div')
      modal.className = 'csv-preview-modal'
      modal.innerHTML = `
        <div class="modal-overlay" onclick="this.parentElement.remove()">
          <div class="modal-content" onclick="event.stopPropagation()">
            <div class="modal-header">
              <h3>CSV文件预览 - ${data.filename}</h3>
              <button class="close-btn" onclick="this.closest('.csv-preview-modal').remove()">&times;</button>
            </div>
            <div class="modal-body">
              <div class="file-info">
                <p>文件名: ${data.filename}</p>
                <p>文件大小: ${formatFileSize(data.fileSize)}</p>
                <p>总行数: ${data.totalRows}</p>
              </div>
              <div class="table-container">
                <table class="csv-table">
                  <thead>
                    <tr>
                      ${Object.keys(data.rows[0] || {}).map(header => `<th>${header}</th>`).join('')}
                    </tr>
                  </thead>
                  <tbody>
                    ${data.rows.map(row => `
                      <tr>
                        ${Object.values(row).map(cell => `<td>${cell}</td>`).join('')}
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn btn-primary" onclick="window.open('/api/csv/${data.fileId}/download', '_blank')">下载文件</button>
              <button class="btn btn-secondary" onclick="this.closest('.csv-preview-modal').remove()">关闭</button>
            </div>
          </div>
        </div>
      `
      
      // 添加样式
      const style = document.createElement('style')
      style.textContent = `
        .csv-preview-modal {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          z-index: 1000;
        }
        .modal-overlay {
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background: rgba(0, 0, 0, 0.5);
          display: flex;
          justify-content: center;
          align-items: center;
          padding: 20px;
        }
        .modal-content {
          background: white;
          border-radius: 8px;
          max-width: 90%;
          max-height: 90%;
          width: 800px;
          display: flex;
          flex-direction: column;
        }
        .modal-header {
          padding: 16px 20px;
          border-bottom: 1px solid #e8e8e8;
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        .modal-header h3 {
          margin: 0;
          font-size: 18px;
        }
        .close-btn {
          background: none;
          border: none;
          font-size: 24px;
          cursor: pointer;
          color: #999;
        }
        .modal-body {
          flex: 1;
          overflow: auto;
          padding: 20px;
        }
        .file-info {
          margin-bottom: 16px;
          padding: 12px;
          background: #f5f5f5;
          border-radius: 4px;
        }
        .file-info p {
          margin: 4px 0;
          font-size: 14px;
        }
        .table-container {
          overflow: auto;
          max-height: 400px;
          border: 1px solid #e8e8e8;
          border-radius: 4px;
        }
        .csv-table {
          width: 100%;
          border-collapse: collapse;
          font-size: 14px;
        }
        .csv-table th,
        .csv-table td {
          padding: 8px 12px;
          text-align: left;
          border-bottom: 1px solid #e8e8e8;
        }
        .csv-table th {
          background: #fafafa;
          font-weight: 600;
          position: sticky;
          top: 0;
        }
        .csv-table tr:hover {
          background: #f5f5f5;
        }
        .modal-footer {
          padding: 16px 20px;
          border-top: 1px solid #e8e8e8;
          display: flex;
          gap: 12px;
          justify-content: flex-end;
        }
        .btn {
          padding: 8px 16px;
          border: none;
          border-radius: 4px;
          cursor: pointer;
          font-size: 14px;
        }
        .btn-primary {
          background: #1890ff;
          color: white;
        }
        .btn-secondary {
          background: #f5f5f5;
          color: #333;
        }
      `
      
      document.head.appendChild(style)
      document.body.appendChild(modal)
    }
    
    const downloadFile = (file) => {
      console.log('下载文件:', file)
      
      if (file.id) {
        const downloadUrl = `/api/csv/${file.id}/download`
        console.log('下载文件URL:', downloadUrl)
        window.open(downloadUrl, '_blank')
      } else {
        alert('文件ID不存在，无法下载')
      }
    }
    
    const getFileExtension = (filename) => {
      if (!filename) return ''
      const lastDot = filename.lastIndexOf('.')
      return lastDot > -1 ? filename.substring(lastDot + 1) : ''
    }
    
    const getFileIcon = (fileType) => {
      if (!fileType) {
        return 'bi bi-file-earmark'
      }
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
    
    // 加载已上传的文件
    const loadUploadedFiles = async () => {
      if (!props.sessionId) {
        console.log('没有sessionId，跳过加载文件')
        return
      }
      
      try {
        console.log('加载已上传文件: sessionId=', props.sessionId)
        const response = await fetch(`/api/csv/session/${props.sessionId}`)
        const result = await response.json()
        
        if (result.success && result.data) {
          console.log('加载到已上传文件:', result.data)
          uploadedFiles.value = result.data.map(file => ({
            ...file,
            status: 'ACTIVE'
          }))
          console.log('已上传文件列表更新:', uploadedFiles.value)
        } else {
          console.log('没有找到已上传文件')
          uploadedFiles.value = []
        }
      } catch (error) {
        console.error('加载已上传文件失败:', error)
        uploadedFiles.value = []
      }
    }
    
    // 监听会话变化，重新加载文件列表
    watch(() => props.sessionId, (newSessionId, oldSessionId) => {
      console.log('会话ID变化:', oldSessionId, '->', newSessionId)
      if (newSessionId && newSessionId !== oldSessionId) {
        loadUploadedFiles()
      } else if (!newSessionId) {
        uploadedFiles.value = []
      }
    })
    
    // 组件挂载时加载已上传文件
    onMounted(() => {
      console.log('FileUpload组件挂载，sessionId:', props.sessionId)
      if (props.sessionId) {
        loadUploadedFiles()
      }
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
      downloadFile,
      loadUploadedFiles,
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
