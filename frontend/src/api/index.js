import request from './request'

export function getStatistics() {
  return request.get('/furniture/statistics')
}

export function getAllFloors() {
  return request.get('/furniture/floors')
}

export function getGroupByFloor() {
  return request.get('/furniture/group-by-floor')
}

export function listFurniture(params) {
  return request.get('/furniture/list', { params })
}

export function pageFurniture(params) {
  return request.get('/furniture/page', { params })
}

export function getFurnitureById(id) {
  return request.get(`/furniture/${id}`)
}

export function getFurnitureByCode(code) {
  return request.get(`/furniture/code/${code}`)
}

export function getFurnitureByFloor(floorNum) {
  return request.get(`/furniture/floor/${floorNum}`)
}

export function getFurnitureByStationCode(stationCode) {
  return request.get(`/furniture/station/${stationCode}`)
}

export function createFurniture(data) {
  return request.post('/furniture', data)
}

export function updateFurniture(data) {
  return request.put('/furniture', data)
}

export function deleteFurniture(id) {
  return request.delete(`/furniture/${id}`)
}

export function bindStation(data) {
  return request.post('/furniture/bind', data)
}

export function unbindStation(data) {
  return request.post('/furniture/unbind', data)
}

export function exportFloor(floorNum) {
  return request.get(`/furniture/export/floor/${floorNum}`, {
    responseType: 'blob'
  })
}

export function exportAll() {
  return request.get('/furniture/export/all', {
    responseType: 'blob'
  })
}

export function getRecordsByFurnitureId(furnitureId) {
  return request.get(`/record/furniture/${furnitureId}`)
}

export function getRecordsByFurnitureCode(furnitureCode) {
  return request.get(`/record/furniture-code/${furnitureCode}`)
}

export function saveSpecTemplate(templateKey, spec) {
  return request.post(`/spec/${templateKey}`, spec)
}

export function getSpecTemplate(templateKey) {
  return request.get(`/spec/${templateKey}`)
}

export function getAllSpecTemplates() {
  return request.get('/spec/list')
}

export function deleteSpecTemplate(templateKey) {
  return request.delete(`/spec/${templateKey}`)
}

export function uploadImage(formData) {
  return request.post('/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
