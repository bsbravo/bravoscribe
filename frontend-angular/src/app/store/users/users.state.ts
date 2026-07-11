import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { AdminUser } from '../../core/models/user.model';

export interface UsersState extends EntityState<AdminUser> {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  loading: boolean;
  error: string | null;
  deactivatingId: string | null;
  searchTerm: string;
}

export const usersAdapter = createEntityAdapter<AdminUser>({
  selectId: (user) => user.id,
});

export const initialUsersState: UsersState = usersAdapter.getInitialState({
  page: 0,
  size: 31, // SYSTEM_SPEC.md default page size
  totalElements: 0,
  totalPages: 0,
  loading: false,
  error: null,
  deactivatingId: null,
  searchTerm: '',
});

export const USERS_FEATURE_KEY = 'users';
