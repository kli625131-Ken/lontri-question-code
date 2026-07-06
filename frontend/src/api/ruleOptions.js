import request from './index'

export function getRuleOptions() {
  return request.get('/rule-options')
}
