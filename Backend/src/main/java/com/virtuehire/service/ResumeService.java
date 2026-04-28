package com.virtuehire.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuehire.model.Candidate;
import com.virtuehire.model.ResumeDocument;
import com.virtuehire.repository.ResumeDocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "personalInfo", "professionalSummary", "skills", "education",
            "experience", "projects", "certifications", "achievements", "keywords");

    private final ResumeDocumentRepository resumeDocumentRepository;
    private final ObjectMapper objectMapper;
    private final Path resumeDir;

    public ResumeService(
            ResumeDocumentRepository resumeDocumentRepository,
            ObjectMapper objectMapper,
            @Value("${file.upload-dir}") String uploadDirPath) {
        this.resumeDocumentRepository = resumeDocumentRepository;
        this.objectMapper = objectMapper;
        this.resumeDir = Paths.get(uploadDirPath).toAbsolutePath().normalize().resolve("generated-resumes");
    }

    public List<Map<String, Object>> listCandidateResumes(Long candidateId) {
        return resumeDocumentRepository.findByCandidateIdOrderByUpdatedAtDesc(candidateId).stream()
                .map(this::toResumeResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCandidateResume(Long candidateId, Long resumeId) {
        ResumeDocument resumeDocument = resumeDocumentRepository.findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        return toResumeResponse(resumeDocument);
    }

    @Transactional
    public Map<String, Object> createResume(Candidate candidate, Map<String, Object> payload) throws IOException {
        ResumeDocument resumeDocument = new ResumeDocument();
        resumeDocument.setCandidate(candidate);
        return saveResume(resumeDocument, payload, true);
    }

    @Transactional
    public Map<String, Object> updateResume(Long candidateId, Long resumeId, Map<String, Object> payload) throws IOException {
        ResumeDocument existing = resumeDocumentRepository.findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        return saveResume(existing, payload, false);
    }

    @Transactional
    public void deleteResume(Long candidateId, Long resumeId) {
        ResumeDocument existing = resumeDocumentRepository.findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        deleteGeneratedFile(existing.getPdfPath());
        resumeDocumentRepository.delete(existing);
    }

    @Transactional
    public void deleteCandidateResumes(Long candidateId) {
        List<ResumeDocument> resumes = resumeDocumentRepository.findByCandidateId(candidateId);
        for (ResumeDocument resume : resumes) {
            deleteGeneratedFile(resume.getPdfPath());
        }
        resumeDocumentRepository.deleteAll(resumes);
    }

    public Path resolveResumePdf(Long candidateId, Long resumeId) {
        ResumeDocument resumeDocument = resumeDocumentRepository.findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        Path filePath = resumeDir.resolve(resumeDocument.getPdfPath()).normalize();
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Resume PDF not found");
        }
        return filePath;
    }

    public String getResumePdfName(Long candidateId, Long resumeId) {
        ResumeDocument resumeDocument = resumeDocumentRepository.findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        return resumeDocument.getPdfPath();
    }

    private Map<String, Object> saveResume(ResumeDocument resumeDocument, Map<String, Object> payload, boolean creating)
            throws IOException {
        Map<String, Object> resumeData = normalizeResumeData(payload);
        Map<String, Object> atsResult = calculateAtsScore(resumeData);

        if (!Files.exists(resumeDir)) {
            Files.createDirectories(resumeDir);
        }

        if (!creating) {
            deleteGeneratedFile(resumeDocument.getPdfPath());
        }

        String title = firstNonBlank(asString(payload.get("title")), buildDefaultTitle(resumeData));
        String templateId = firstNonBlank(asString(payload.get("templateId")), "modern-slate");
        String pdfName = buildPdfName(title);

        resumeDocument.setTitle(title);
        resumeDocument.setTemplateId(templateId);
        resumeDocument.setResumeDataJson(objectMapper.writeValueAsString(resumeData));
        resumeDocument.setPdfPath(pdfName);
        resumeDocument.setAtsScore((Integer) atsResult.get("score"));
        resumeDocument.setMissingKeywords(String.join(", ", castStringList(atsResult.get("missingKeywords"))));
        resumeDocument.setSuggestions(String.join(" || ", castStringList(atsResult.get("suggestions"))));
        resumeDocument.setUpdatedAt(LocalDateTime.now());

        generatePdf(resumeData, title, templateId, resumeDir.resolve(pdfName));

        ResumeDocument saved = resumeDocumentRepository.save(resumeDocument);
        return toResumeResponse(saved);
    }

    private Map<String, Object> toResumeResponse(ResumeDocument resumeDocument) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", resumeDocument.getId());
        response.put("title", resumeDocument.getTitle());
        response.put("templateId", resumeDocument.getTemplateId());
        response.put("atsScore", resumeDocument.getAtsScore());
        response.put("createdAt", resumeDocument.getCreatedAt());
        response.put("updatedAt", resumeDocument.getUpdatedAt());
        response.put("pdfPath", resumeDocument.getPdfPath());
        response.put("resumeData", readResumeData(resumeDocument.getResumeDataJson()));
        response.put("missingKeywords", splitStoredText(resumeDocument.getMissingKeywords(), ","));
        response.put("suggestions", splitStoredText(resumeDocument.getSuggestions(), "\\|\\|"));
        return response;
    }

    private Map<String, Object> readResumeData(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read resume data", ex);
        }
    }

    private List<String> splitStoredText(String value, String delimiterRegex) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(delimiterRegex))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toList());
    }

    private Map<String, Object> normalizeResumeData(Map<String, Object> payload) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        Map<String, Object> personalInfo = castMap(payload.get("personalInfo"));
        normalized.put("personalInfo", new LinkedHashMap<>(Map.of(
                "name", asString(personalInfo.get("name")),
                "email", asString(personalInfo.get("email")),
                "phone", asString(personalInfo.get("phone")),
                "location", asString(personalInfo.get("location")),
                "linkedin", asString(personalInfo.get("linkedin")),
                "portfolio", asString(personalInfo.get("portfolio"))
        )));

        normalized.put("professionalSummary", asString(payload.get("professionalSummary")));
        normalized.put("skills", sanitizeStringList(payload.get("skills")));
        normalized.put("education", sanitizeObjectList(payload.get("education"), List.of("institution", "degree", "duration", "description")));
        normalized.put("experience", sanitizeObjectList(payload.get("experience"), List.of("company", "role", "duration", "description")));
        normalized.put("projects", sanitizeObjectList(payload.get("projects"), List.of("name", "role", "duration", "description")));
        normalized.put("certifications", sanitizeObjectList(payload.get("certifications"), List.of("name", "issuer", "year", "description")));
        normalized.put("achievements", sanitizeStringList(payload.get("achievements")));
        normalized.put("keywords", sanitizeStringList(payload.get("keywords")));

        return normalized;
    }

    private Map<String, Object> calculateAtsScore(Map<String, Object> resumeData) {
        int sectionScore = 0;
        int keywordScore = 0;
        List<String> suggestions = new ArrayList<>();

        Map<String, Object> personalInfo = castMap(resumeData.get("personalInfo"));
        int completedSections = 0;

        for (String section : REQUIRED_SECTIONS) {
            if (isSectionComplete(section, resumeData, personalInfo)) {
                completedSections++;
            }
        }

        sectionScore = (int) Math.round((completedSections / (double) REQUIRED_SECTIONS.size()) * 60);

        List<String> keywords = sanitizeStringList(resumeData.get("keywords"));
        String searchableText = buildSearchableText(resumeData);
        List<String> missingKeywords = keywords.stream()
                .filter(keyword -> !searchableText.contains(keyword.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        if (!keywords.isEmpty()) {
            keywordScore = (int) Math.round(((keywords.size() - missingKeywords.size()) / (double) keywords.size()) * 40);
        }

        if (!personalInfoComplete(personalInfo)) {
            suggestions.add("Fill in all personal info fields so recruiters and ATS systems can index your contact details.");
        }
        if (asString(resumeData.get("professionalSummary")).isBlank()) {
            suggestions.add("Add a concise professional summary with your strongest role, experience, and domain keywords.");
        }
        if (sanitizeStringList(resumeData.get("skills")).size() < 5) {
            suggestions.add("List at least 5 relevant skills to improve keyword coverage.");
        }
        if (sanitizeObjectList(resumeData.get("experience"), List.of("company", "role", "duration", "description")).isEmpty()) {
            suggestions.add("Add at least one experience entry with measurable impact in the description.");
        }
        if (missingKeywords.isEmpty()) {
            suggestions.add("Keyword coverage looks strong. Keep role-specific terms aligned with the target job description.");
        } else {
            suggestions.add("Include missing ATS keywords naturally in summary, skills, experience, or projects.");
        }

        int finalScore = Math.max(0, Math.min(100, sectionScore + keywordScore));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", finalScore);
        response.put("missingKeywords", missingKeywords);
        response.put("suggestions", suggestions.stream().distinct().collect(Collectors.toList()));
        return response;
    }

    private boolean isSectionComplete(String section, Map<String, Object> resumeData, Map<String, Object> personalInfo) {
        return switch (section) {
            case "personalInfo" -> personalInfoComplete(personalInfo);
            case "professionalSummary" -> !asString(resumeData.get("professionalSummary")).isBlank();
            case "skills" -> !sanitizeStringList(resumeData.get("skills")).isEmpty();
            case "education", "experience", "projects", "certifications" ->
                    !castList(resumeData.get(section)).isEmpty();
            case "achievements", "keywords" -> !sanitizeStringList(resumeData.get(section)).isEmpty();
            default -> false;
        };
    }

    private boolean personalInfoComplete(Map<String, Object> personalInfo) {
        return !asString(personalInfo.get("name")).isBlank()
                && !asString(personalInfo.get("email")).isBlank()
                && !asString(personalInfo.get("phone")).isBlank()
                && !asString(personalInfo.get("location")).isBlank();
    }

    private String buildSearchableText(Map<String, Object> resumeData) {
        StringBuilder builder = new StringBuilder();
        appendMapValues(builder, castMap(resumeData.get("personalInfo")));
        builder.append(" ").append(asString(resumeData.get("professionalSummary")));
        sanitizeStringList(resumeData.get("skills")).forEach(value -> builder.append(" ").append(value));
        sanitizeStringList(resumeData.get("achievements")).forEach(value -> builder.append(" ").append(value));

        for (String section : List.of("education", "experience", "projects", "certifications")) {
            for (Map<String, String> item : sanitizeObjectList(resumeData.get(section), List.of())) {
                item.values().forEach(value -> builder.append(" ").append(value));
            }
        }

        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private void appendMapValues(StringBuilder builder, Map<String, Object> values) {
        values.values().forEach(value -> builder.append(" ").append(asString(value)));
    }

    private void generatePdf(Map<String, Object> resumeData, String title, String templateId, Path filePath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float margin = 48f;
            float y = page.getMediaBox().getHeight() - margin;
            float width = page.getMediaBox().getWidth() - (margin * 2);
            float leading = 15f;

            Map<String, Object> personalInfo = castMap(resumeData.get("personalInfo"));

            contentStream.setNonStrokingColor(28, 52, 84);
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 20);
            contentStream.newLineAtOffset(margin, y);
            contentStream.showText(trimForPdf(firstNonBlank(asString(personalInfo.get("name")), title)));
            contentStream.endText();
            y -= 24;

            String contactLine = String.join(" | ", sanitizeStringList(List.of(
                    asString(personalInfo.get("email")),
                    asString(personalInfo.get("phone")),
                    asString(personalInfo.get("location")),
                    asString(personalInfo.get("linkedin")),
                    asString(personalInfo.get("portfolio"))
            )));

            y = writeWrappedText(contentStream, trimForPdf(contactLine), margin, y, width, 10, leading, PDType1Font.HELVETICA);
            y -= 10;

            y = writeSection(contentStream, "Professional Summary", List.of(asString(resumeData.get("professionalSummary"))), margin, y, width);
            y = writeSection(contentStream, "Skills", List.of(String.join(", ", sanitizeStringList(resumeData.get("skills")))), margin, y, width);
            y = writeObjectSection(contentStream, "Experience", sanitizeObjectList(resumeData.get("experience"), List.of("company", "role", "duration", "description")), margin, y, width);
            y = writeObjectSection(contentStream, "Education", sanitizeObjectList(resumeData.get("education"), List.of("institution", "degree", "duration", "description")), margin, y, width);
            y = writeObjectSection(contentStream, "Projects", sanitizeObjectList(resumeData.get("projects"), List.of("name", "role", "duration", "description")), margin, y, width);
            y = writeObjectSection(contentStream, "Certifications", sanitizeObjectList(resumeData.get("certifications"), List.of("name", "issuer", "year", "description")), margin, y, width);
            y = writeSection(contentStream, "Achievements", sanitizeStringList(resumeData.get("achievements")), margin, y, width);
            y = writeSection(contentStream, "ATS Keywords", List.of(String.join(", ", sanitizeStringList(resumeData.get("keywords")))), margin, y, width);

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
            contentStream.newLineAtOffset(margin, 24);
            contentStream.showText(trimForPdf("Generated with template: " + templateId));
            contentStream.endText();
            contentStream.close();

            document.save(filePath.toFile());
        }
    }

    private float writeSection(PDPageContentStream contentStream, String heading, List<String> lines,
                               float margin, float y, float width) throws IOException {
        List<String> filteredLines = lines.stream()
                .map(this::asString)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());

        if (filteredLines.isEmpty()) {
            return y;
        }

        contentStream.setNonStrokingColor(37, 99, 235);
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.newLineAtOffset(margin, y);
        contentStream.showText(trimForPdf(heading));
        contentStream.endText();
        y -= 16;

        contentStream.setNonStrokingColor(15, 23, 42);
        for (String line : filteredLines) {
            y = writeWrappedText(contentStream, trimForPdf(line), margin, y, width, 10, 14, PDType1Font.HELVETICA);
        }

        return y - 10;
    }

    private float writeObjectSection(PDPageContentStream contentStream, String heading, List<Map<String, String>> items,
                                     float margin, float y, float width) throws IOException {
        if (items.isEmpty()) {
            return y;
        }

        contentStream.setNonStrokingColor(37, 99, 235);
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.newLineAtOffset(margin, y);
        contentStream.showText(trimForPdf(heading));
        contentStream.endText();
        y -= 16;

        contentStream.setNonStrokingColor(15, 23, 42);
        for (Map<String, String> item : items) {
            String headline = item.values().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .limit(3)
                    .collect(Collectors.joining(" | "));

            if (!headline.isBlank()) {
                y = writeWrappedText(contentStream, trimForPdf(headline), margin, y, width, 10, 14, PDType1Font.HELVETICA_BOLD);
            }

            String description = firstNonBlank(item.get("description"), "");
            if (!description.isBlank()) {
                y = writeWrappedText(contentStream, trimForPdf(description), margin + 10, y, width - 10, 10, 14, PDType1Font.HELVETICA);
            }

            y -= 6;
        }

        return y - 8;
    }

    private float writeWrappedText(PDPageContentStream contentStream, String text, float x, float y, float width,
                                   float fontSize, float leading, PDType1Font font) throws IOException {
        if (text == null || text.isBlank()) {
            return y;
        }

        List<String> lines = wrapText(text, width, font, fontSize);
        for (String line : lines) {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(trimForPdf(line));
            contentStream.endText();
            y -= leading;
        }
        return y;
    }

    private List<String> wrapText(String text, float width, PDType1Font font, float fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String tentative = currentLine.isEmpty() ? word : currentLine + " " + word;
            float textWidth = font.getStringWidth(tentative) / 1000 * fontSize;
            if (textWidth > width && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(tentative);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private String buildDefaultTitle(Map<String, Object> resumeData) {
        Map<String, Object> personalInfo = castMap(resumeData.get("personalInfo"));
        String name = asString(personalInfo.get("name"));
        return name.isBlank() ? "Resume Draft" : name + " Resume";
    }

    private String buildPdfName(String title) {
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "resume";
        }
        return slug + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".pdf";
    }

    private void deleteGeneratedFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resumeDir.resolve(fileName).normalize());
        } catch (IOException ignored) {
        }
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> response = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> response.put(String.valueOf(key), itemValue));
            return response;
        }
        return new LinkedHashMap<>();
    }

    private List<Object> castList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private List<Map<String, String>> sanitizeObjectList(Object value, List<String> preferredKeys) {
        List<Map<String, String>> sanitized = new ArrayList<>();
        for (Object item : castList(value)) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, String> normalized = new LinkedHashMap<>();
            if (preferredKeys.isEmpty()) {
                map.forEach((key, itemValue) -> normalized.put(String.valueOf(key), asString(itemValue)));
            } else {
                for (String key : preferredKeys) {
                    normalized.put(key, asString(map.get(key)));
                }
            }

            boolean hasContent = normalized.values().stream().anyMatch(part -> part != null && !part.isBlank());
            if (hasContent) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private List<String> sanitizeStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::asString)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (value instanceof String text) {
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::asString).filter(item -> !item.isBlank()).collect(Collectors.toList());
        }
        return List.of();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimForPdf(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
