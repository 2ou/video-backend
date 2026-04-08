import http from '../utils/http'


export const loginApi = (username: string, password: string) =>
  http.post<BaseResponse<{ access_token: string; token_type: string }>>('/api/v1/auth/login', { username, password })

export const meApi = () => http.get<BaseResponse<any>>('/api/v1/auth/me')
