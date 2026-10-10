package ma.schoolz.erp.homework.api;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.homework.application.HomeworkService;
import ma.schoolz.erp.homework.application.HomeworkService.AttachmentContent;
import ma.schoolz.erp.homework.application.HomeworkService.AttachmentView;
import ma.schoolz.erp.homework.application.HomeworkService.Dashboard;
import ma.schoolz.erp.homework.application.HomeworkService.HomeworkCommand;
import ma.schoolz.erp.homework.application.HomeworkService.HomeworkView;
import ma.schoolz.erp.homework.application.HomeworkService.LessonContentCommand;
import ma.schoolz.erp.homework.application.HomeworkService.LessonContentView;
import ma.schoolz.erp.homework.application.HomeworkService.ReferenceData;
import ma.schoolz.erp.homework.application.HomeworkValidationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/homework")
public class HomeworkController {
    private final HomeworkService homework;

    public HomeworkController(HomeworkService homework) {
        this.homework = homework;
    }

    @GetMapping("/references")
    public ReferenceData references(@AuthenticationPrincipal SchoolPrincipal actor) {
        return homework.references(actor);
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam UUID classroomId,
            @RequestParam UUID academicTermId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return homework.dashboard(actor, classroomId, academicTermId, from, to);
    }

    @PostMapping("/lesson-contents")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonContentView createLessonContent(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody LessonContentCommand command,
            HttpServletRequest request) {
        return homework.createLessonContent(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/lesson-contents/{id}")
    public LessonContentView updateLessonContent(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            @RequestBody LessonContentCommand command,
            HttpServletRequest request) {
        return homework.updateLessonContent(actor, id, command, request.getRemoteAddr());
    }

    @PostMapping("/lesson-contents/{id}/publish")
    public LessonContentView publishLessonContent(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            HttpServletRequest request) {
        return homework.publishLessonContent(actor, id, request.getRemoteAddr());
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public HomeworkView createHomework(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody HomeworkCommand command,
            HttpServletRequest request) {
        return homework.createHomework(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/assignments/{id}")
    public HomeworkView updateHomework(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            @RequestBody HomeworkCommand command,
            HttpServletRequest request) {
        return homework.updateHomework(actor, id, command, request.getRemoteAddr());
    }

    @PostMapping("/assignments/{id}/publish")
    public HomeworkView publishHomework(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            HttpServletRequest request) {
        return homework.publishHomework(actor, id, request.getRemoteAddr());
    }

    @PostMapping("/assignments/{id}/close")
    public HomeworkView closeHomework(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            HttpServletRequest request) {
        return homework.closeHomework(actor, id, request.getRemoteAddr());
    }

    @PostMapping(
            value = "/lesson-contents/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView lessonAttachment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return upload(actor, "LESSON_CONTENT", id, file, request.getRemoteAddr());
    }

    @PostMapping(
            value = "/assignments/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView homeworkAttachment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return upload(actor, "HOMEWORK", id, file, request.getRemoteAddr());
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> attachment(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID id) {
        return file(homework.attachment(actor, id));
    }

    private AttachmentView upload(
            SchoolPrincipal actor,
            String ownerType,
            UUID ownerId,
            MultipartFile file,
            String ipAddress) {
        try {
            return homework.uploadAttachment(
                    actor,
                    ownerType,
                    ownerId,
                    file == null ? null : file.getOriginalFilename(),
                    file == null ? null : file.getContentType(),
                    file == null ? null : file.getBytes(),
                    ipAddress);
        } catch (IOException exception) {
            throw new HomeworkValidationException("Le fichier n'a pas pu être lu");
        }
    }

    static ResponseEntity<byte[]> file(AttachmentContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.bytes().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.fileName())
                                .build()
                                .toString())
                .header("X-Content-SHA256", content.sha256())
                .body(content.bytes());
    }
}
