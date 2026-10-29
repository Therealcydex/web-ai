import { Component, Input, OnChanges, inject } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { AiResponse } from '../core/models';
import { AiMarkdownComponent } from '../shared/ai-markdown.component';
import { ThemeCount } from './review-api.service';

/** Aggregate-only AI context: counts, average and server-counted themes, never review text. */
export function reviewFacts(count: number, rating: number, themes: ThemeCount[] = []) {
  return [
    `reviews count=${count}`,
    `rating average=${Number(rating).toFixed(2)}`,
    ...themes.flatMap((t) => [`${t.id} positive=${t.positive}`, `${t.id} negative=${t.negative}`]),
  ].join('; ');
}

@Component({
  selector: 'app-review-summary',
  standalone: true,
  imports: [AiMarkdownComponent],
  template: `<section class="inset mt-4" aria-label="AI review summary">
    <strong>AI review summary</strong>
    <p class="muted text-sm">
      A quick reading of these ratings{{ themes.length ? ' and recurring themes' : '' }}. Read the
      individual reviews for the full story.
    </p>
    <button class="btn secondary small mt-3" [disabled]="busy || !count" (click)="summarize()">
      {{ busy ? 'Summarizing…' : 'Summarize these reviews' }}
    </button>
    @if (!count) {
      <p class="muted text-sm mt-2">A summary becomes available after the first review.</p>
    }
    @if (error) {
      <p class="error-text" role="alert">{{ error }}</p>
    }
    @if (response) {
      <span class="badge mt-3">{{
        response.mode === 'live' ? 'AI model response' : 'Offline response'
      }}</span>
      <app-ai-markdown class="mt-3" [text]="response.text" />
      <p class="muted text-xs">{{ response.notice }} Review before sharing.</p>
    }
  </section>`,
})
export class ReviewSummaryComponent implements OnChanges {
  private readonly api = inject(ApiService);
  @Input() count = 0;
  @Input() rating = 0;
  @Input() themes: ThemeCount[] = [];
  @Input() scope = '';
  busy = false;
  error = '';
  response: AiResponse | null = null;
  private version = 0;
  ngOnChanges() {
    this.version++;
    this.response = null;
    this.error = '';
    this.busy = false;
  }
  async summarize() {
    if (this.busy || !this.count) return;
    const version = ++this.version;
    this.busy = true;
    this.error = '';
    this.response = null;
    try {
      const response = await this.api.assist(
        'review-summary',
        reviewFacts(this.count, this.rating, this.themes),
      );
      if (version === this.version) this.response = response;
    } catch (e) {
      if (version === this.version) this.error = errorMessage(e);
    } finally {
      if (version === this.version) this.busy = false;
    }
  }
}
