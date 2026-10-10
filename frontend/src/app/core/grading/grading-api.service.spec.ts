import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GradingApiService } from './grading-api.service';

describe('GradingApiService', () => {
  let service: GradingApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GradingApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a dashboard in an explicit class and term scope', () => {
    service.dashboard('class-1', 'term-1').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/grading/dashboard' &&
        candidate.params.get('classroomId') === 'class-1' &&
        candidate.params.get('academicTermId') === 'term-1',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('sends the correction reason with a grade update', () => {
    service
      .saveGrade('assessment-1', 'student-1', {
        rawScore: 14.5,
        status: 'GRADED',
        reason: 'Copie revérifiée',
      })
      .subscribe();
    const request = http.expectOne('/api/v1/grading/assessments/assessment-1/grades/student-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.reason).toBe('Copie revérifiée');
    request.flush({});
  });

  it('keeps the grading formula as server-side configuration', () => {
    service
      .savePolicy({
        schoolLevelId: 'primary',
        evaluationMode: 'HYBRID',
        calculationStrategy: 'CATEGORY_WEIGHTED',
        calculationConfig: { categoryWeights: { CONTINUOUS: 60, EXAM: 40 } },
      })
      .subscribe();
    const request = http.expectOne('/api/v1/grading/policies');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.calculationConfig.categoryWeights.EXAM).toBe(40);
    request.flush({});
  });

  it('calculates report cards with an optional versioned template', () => {
    service.calculateReportCards('class-1', 'term-1', 'template-1').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/grading/report-cards/calculate' &&
        candidate.params.get('classroomId') === 'class-1' &&
        candidate.params.get('academicTermId') === 'term-1' &&
        candidate.params.get('templateId') === 'template-1',
    );
    expect(request.request.method).toBe('POST');
    request.flush([]);
  });

  it('previews, publishes and downloads one immutable report-card version', () => {
    const command = {
      languageMode: 'BILINGUAL' as const,
      signerName: 'Nadia El Mansouri',
      signerTitle: 'Directrice',
    };

    service.previewReportCard('report-1', command).subscribe();
    const preview = http.expectOne('/api/v1/grading/report-cards/report-1/preview');
    expect(preview.request.method).toBe('POST');
    expect(preview.request.responseType).toBe('blob');
    expect(preview.request.body.languageMode).toBe('BILINGUAL');
    preview.flush(new Blob(['pdf'], { type: 'application/pdf' }));

    service.publishReportCard('report-1', command).subscribe();
    const publish = http.expectOne('/api/v1/grading/report-cards/report-1/publish');
    expect(publish.request.method).toBe('POST');
    expect(publish.request.body.signerName).toBe('Nadia El Mansouri');
    publish.flush({ id: 'report-1', status: 'PUBLISHED' });

    service.downloadReportCard('report-1').subscribe();
    const download = http.expectOne('/api/v1/grading/report-cards/report-1/pdf');
    expect(download.request.method).toBe('GET');
    expect(download.request.responseType).toBe('blob');
    download.flush(new Blob(['pdf'], { type: 'application/pdf' }));
  });
});
