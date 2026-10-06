package com.exam.allocation.controller;

import com.exam.allocation.service.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** File upload endpoint for importing student lists from PDF / Excel / CSV files. */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    @Autowired
    private ImportService importService;

    @PostMapping("/students")
    public ImportService.ImportResult importStudents(@RequestParam("file") MultipartFile file) throws IOException {
        return importService.importStudents(file);
    }
}
