//package com.virtuehire.controller;
//
//import com.virtuehire.model.Hr;
//import com.virtuehire.service.HrService;
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.UrlResource;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.List;
//
//@Controller
//@RequestMapping("/hrs")
//public class HrController {
//
//    private final HrService service;
//    private final String uploadDir = "E:/eclipse/virtuehire-backend/uploads"; // ensure this folder exists
//
//    public HrController(HrService service) {
//        this.service = service;
//    }
//
//    // Show HR registration form
//    @GetMapping("/register")
//    public String showForm(Model model) {
//        model.addAttribute("hr", new Hr());
//        return "hr-form";  // matches hr-form.html
//    }
//
//    // Handle HR registration with file upload
//    @PostMapping("/register")
//    public String register(@ModelAttribute Hr hr,
//                           @RequestParam("idProofFile") MultipartFile file,
//                           Model model) {
//        try {
//            if (!file.isEmpty()) {
//                File dir = new File(uploadDir);
//                if (!dir.exists()) dir.mkdirs();
//
//                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
//                Path filePath = Paths.get(uploadDir, fileName);
//                file.transferTo(filePath.toFile());
//
//                hr.setIdProofPath(fileName); // save filename in DB
//            }
//
//            service.save(hr);
//
//            model.addAttribute("message", "HR registered successfully!");
//            model.addAttribute("hr", new Hr()); // reset form
//            return "hr-form";
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            model.addAttribute("message", "❌ Error uploading file: " + e.getMessage());
//            return "hr-form";
//        }
//    }
//
//    // Show list of all HRs
//    @GetMapping("/list")
//    public String list(Model model) {
//        List<Hr> hrs = service.findAll();
//        model.addAttribute("hrs", hrs);
//        return "hr-list"; // matches hr-list.html
//    }
//
//    // Serve uploaded ID proof files
//    @GetMapping("/file/{filename}")
//    @ResponseBody
//    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
//        Path path = Paths.get(uploadDir).resolve(filename).normalize();
//        Resource resource = new UrlResource(path.toUri());
//        if (!resource.exists()) return ResponseEntity.notFound().build();
//
//        String contentType = Files.probeContentType(path);
//        if (contentType == null) contentType = "application/octet-stream";
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION,
//                        "inline; filename=\"" + resource.getFilename() + "\"")
//                .header(HttpHeaders.CONTENT_TYPE, contentType)
//                .body(resource);
//    }
//
//    // Show HR login page
//    @GetMapping("/login")
//    public String loginPage() {
//        return "hr-login"; // matches hr-login.html
//    }
//
//    // Handle HR login
//    @PostMapping("/login")
//    public String login(@RequestParam String email,
//                        @RequestParam String password,
//                        Model model) {
//        Hr hr = service.login(email, password);
//        if (hr != null) {
//            model.addAttribute("name", hr.getFullName());
//            return "hr-welcome"; // matches hr-welcome.html
//        } else {
//            model.addAttribute("error", "Invalid credentials");
//            return "hr-login";
//        }
//    }
//}
