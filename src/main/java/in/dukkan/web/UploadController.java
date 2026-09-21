package in.dukkan.web;

import in.dukkan.service.UploadService;
import in.dukkan.service.UploadService.UploadResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploads;

    public UploadController(UploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping
    public UploadResponse upload(@RequestPart("file") MultipartFile file) {
        return uploads.store(file);
    }
}
