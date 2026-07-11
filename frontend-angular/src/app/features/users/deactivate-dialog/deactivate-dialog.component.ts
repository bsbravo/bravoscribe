import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-deactivate-dialog',
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './deactivate-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeactivateDialogComponent {
  constructor(private readonly dialogRef: MatDialogRef<DeactivateDialogComponent, boolean>) {}

  cancel(): void {
    this.dialogRef.close(false);
  }

  confirm(): void {
    this.dialogRef.close(true);
  }
}
