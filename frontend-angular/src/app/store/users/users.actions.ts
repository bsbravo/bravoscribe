import { createActionGroup, props } from '@ngrx/store';
import { UserPage } from '../../core/models/user.model';

export const UsersActions = createActionGroup({
  source: 'Users',
  events: {
    'Page Requested': props<{ page: number; size: number }>(),
    'Page Loaded': props<UserPage>(),
    'Page Load Failure': props<{ error: string }>(),

    'Deactivate Requested': props<{ id: string }>(),
    'Deactivate Success': props<{ id: string }>(),
    'Deactivate Failure': props<{ id: string; error: string }>(),

    'Search Term Changed': props<{ term: string }>(),
  },
});
