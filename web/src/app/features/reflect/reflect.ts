import { Component } from '@angular/core';

@Component({
  selector: 'app-reflect',
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Reflect</h1>
        <p class="page__lede">
          Your reflections on delivering Simplicity, searchable and private to you.
        </p>
      </header>

      <div class="card">
        <div class="empty">
          <p><strong>No reflections yet</strong></p>
          <p>Writing and searching reflections arrives in the next release.</p>
        </div>
      </div>
    </div>
  `,
})
export class Reflect {}
