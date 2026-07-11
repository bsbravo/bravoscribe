// Mirrors com.bravoscribe.userservice.dto.UserResponse (services/user-service).
export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: 'ADMIN' | 'USER';
  active: boolean;
  createdAt: string; // ISO instant
}

// Mirrors org.springframework.data.domain.Page<UserResponse> as serialized by Jackson.
export interface UserPage {
  content: AdminUser[];
  number: number; // current page, 0-based
  size: number; // page size
  totalElements: number;
  totalPages: number;
}
