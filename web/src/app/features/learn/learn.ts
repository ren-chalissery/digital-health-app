import { Component } from '@angular/core';

@Component({
  selector: 'app-learn',
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Learn</h1>
        <p class="page__lede">
          Training modules covering how to introduce and support Simplicity with the people you
          work with.
        </p>
      </header>

      <div class="card">
        <div class="empty">
          <p><strong>No modules published yet</strong></p>
          <p>Module viewing, completion, and quizzes arrive in the next release.</p>
        </div>
      </div>
    </div>
  `,
})
export class Learn {}
