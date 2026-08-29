import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ReflectionsApi } from '../../api/api/reflections.service';
import { ReflectionResponse } from '../../api/model/reflection-response';
import { problemMessage } from '../../core/problem';
import { findIdentifiers } from './identifiers';

@Component({
  selector: 'app-reflect',
  imports: [DatePipe, FormsModule],
  styleUrl: './reflect.scss',
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Reflect</h1>
        <p class="page__lede">
          Your own notes on your practice. Nobody else can read these — not your colleagues, not an
          administrator.
        </p>
      </header>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }

      <div class="card">
        <div class="card__title">
          <h2>{{ editingId() ? 'Edit reflection' : 'New reflection' }}</h2>
          @if (editingId()) {
            <button class="button--link" type="button" (click)="clear()">Cancel</button>
          }
        </div>

        <div class="field">
          <label for="title">Title <span class="field__hint">(optional)</span></label>
          <input id="title" [ngModel]="title" (ngModelChange)="onTitle($event)" name="title" />
        </div>

        <div class="field">
          <label for="body">What happened, and what you noticed</label>
          <textarea
            id="body"
            rows="8"
            [ngModel]="body"
            (ngModelChange)="onBody($event)"
            name="body"
          ></textarea>
        </div>

        @if (warnings().length > 0) {
          <div class="notice notice--warning" role="status">
            <p>
              <strong>This looks like it might identify somebody.</strong>
              We spotted what could be
              {{ warningList() }}.
            </p>
            @for (warning of warnings(); track warning.kind) {
              <p class="field__hint">{{ warning.explanation }}</p>
            }
            <p class="field__hint">
              Reflections are meant to be about your own practice rather than about a particular
              person, which is what keeps them outside clinical record-keeping rules. You can save
              anyway if we have got this wrong.
            </p>
          </div>
        }

        <div class="row">
          <button class="button" type="button" [disabled]="busy() || !body.trim()" (click)="save()">
            {{ busy() ? 'Saving…' : editingId() ? 'Save changes' : 'Save reflection' }}
          </button>
        </div>
      </div>

      <div class="card">
        <div class="card__title">
          <h2>Your reflections</h2>
        </div>

        <div class="field">
          <label for="search">Search</label>
          <input
            id="search"
            [(ngModel)]="query"
            name="search"
            (keyup.enter)="load()"
            placeholder="Words from a reflection"
          />
        </div>
        <div class="row">
          <button class="button--link" type="button" (click)="load()">Search</button>
          @if (query) {
            <button class="button--link" type="button" (click)="query = ''; load()">Clear</button>
          }
        </div>

        @if (loading()) {
          <p>Loading…</p>
        } @else if (entries().length === 0) {
          <div class="empty">
            <p><strong>{{ query ? 'Nothing matched' : 'Nothing written yet' }}</strong></p>
            @if (!query) {
              <p>
                A reflection is a note to yourself about how something went. Write about your own
                practice rather than about a particular person, and leave out anything that would
                identify them.
              </p>
            }
          </div>
        } @else {
          @for (entry of entries(); track entry.id) {
            <article class="entry">
              <div class="card__title">
                <h3>{{ entry.title || 'Untitled' }}</h3>
                <span class="field__hint">{{ entry.createdAt | date: 'd MMM y' }}</span>
              </div>
              <p class="entry__body">{{ entry.body }}</p>
              <div class="row">
                <button class="button--link" type="button" (click)="edit(entry)">Edit</button>
                <button class="button--link" type="button" (click)="remove(entry)">Delete</button>
              </div>
            </article>
          }
        }
      </div>
    </div>
  `,
})
export class Reflect implements OnInit {
  private readonly api = inject(ReflectionsApi);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly entries = signal<ReflectionResponse[]>([]);
  protected readonly editingId = signal<string | null>(null);

  protected title = '';
  protected body = '';
  protected query = '';

  // A signal mirroring the fields, so the warning recomputes as they type rather than on save.
  // The title is checked too: a name is as likely to land there as in the body.
  private readonly draft = signal('');
  protected readonly warnings = computed(() => findIdentifiers(this.draft()));

  protected readonly warningList = computed(() =>
    this.warnings()
      .map((warning) => warning.kind)
      .join(', ')
      .replace(/, ([^,]*)$/, ' or $1'),
  );

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected onTitle(value: string): void {
    this.title = value;
    this.draft.set(this.title + ' ' + this.body);
  }

  protected onBody(value: string): void {
    this.body = value;
    this.draft.set(this.title + ' ' + this.body);
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.entries.set(await firstValueFrom(this.api.listReflections(this.query || undefined)));
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not load your reflections.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async save(): Promise<void> {
    if (!this.body.trim() || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const request = { title: this.title.trim() || undefined, body: this.body };
      const id = this.editingId();
      await firstValueFrom(
        id ? this.api.editReflection(id, request) : this.api.writeReflection(request),
      );
      this.clear();
      await this.load();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not save that reflection.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected edit(entry: ReflectionResponse): void {
    this.editingId.set(entry.id ?? null);
    this.title = entry.title ?? '';
    this.body = entry.body ?? '';
    this.draft.set(this.title + ' ' + this.body);
  }

  protected async remove(entry: ReflectionResponse): Promise<void> {
    if (!entry.id) {
      return;
    }
    try {
      await firstValueFrom(this.api.deleteReflection(entry.id));
      if (this.editingId() === entry.id) {
        this.clear();
      }
      await this.load();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not delete that reflection.'));
    }
  }

  protected clear(): void {
    this.editingId.set(null);
    this.title = '';
    this.body = '';
    this.draft.set('');
  }
}
