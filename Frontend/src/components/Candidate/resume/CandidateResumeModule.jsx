// PATH: Frontend/src/components/Candidate/resume/CandidateResumeModule.jsx

import React, { useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  Download,
  Eye,
  FilePlus2,
  PencilLine,
  Plus,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import {
  createResume,
  deleteResume,
  fetchResumes,
  fetchResumePdfBlob,
  updateResume,
} from "./resumeApi";
import { updateCandidateProfile } from "../profile/profileApi";
import { getCandidateFileUrl, getResumeFileName, isPdfResume } from "../profile/profileUtils";
import { ResumeTemplateMiniPreview, ResumeTemplateSheet } from "./ResumeTemplateRenderer";
import "./CandidateResumeModule.css";

const RESUME_TEMPLATES = [
  {
    id: "classic",
    name: "Classic Professional",
    accent: "linear-gradient(135deg, #111827, #6b7280)",
    summary: "Traditional single-column ATS format with clean section rules.",
    labels: ["ATS Safe", "Traditional", "Readable"],
  },
  {
    id: "modern",
    name: "Modern Minimal",
    accent: "linear-gradient(135deg, #0f172a, #38bdf8)",
    summary: "Sleek layout with strong spacing and a lightweight modern header.",
    labels: ["Modern", "Minimal", "Clean"],
  },
  {
    id: "executive",
    name: "Two-Column Executive",
    accent: "linear-gradient(135deg, #1f2937, #475569)",
    summary: "Professional sidebar layout for leadership and experienced profiles.",
    labels: ["Executive", "Sidebar", "Compact"],
  },
  {
    id: "structured",
    name: "Clean Structured",
    accent: "linear-gradient(135deg, #1e3a8a, #2563eb)",
    summary: "Organized resume with a strong header and clear section hierarchy.",
    labels: ["Structured", "Blue", "Organized"],
  },
  {
    id: "elegant",
    name: "Simple Elegant",
    accent: "linear-gradient(135deg, #334155, #c084fc)",
    summary: "Centered refined style with polished ATS-friendly sections.",
    labels: ["Elegant", "Refined", "Simple"],
  },
];

const EMPTY_ENTRY = { institution: "", degree: "", duration: "", description: "" };
const EMPTY_EXPERIENCE = { company: "", role: "", duration: "", description: "" };
const EMPTY_PROJECT = { name: "", role: "", duration: "", description: "" };
const EMPTY_CERTIFICATION = { name: "", issuer: "", year: "", description: "" };

const createEmptyResume = (candidate) => ({
  title: candidate?.fullName ? `${candidate.fullName} Resume` : "Resume Draft",
  templateId: "",
  personalInfo: {
    name: candidate?.fullName || "",
    email: candidate?.email || "",
    phone: candidate?.phoneNumber || "",
    location: [candidate?.city, candidate?.state].filter(Boolean).join(", "),
    linkedin: "",
    portfolio: "",
  },
  professionalSummary: "",
  skills: [],
  education: [{ ...EMPTY_ENTRY }],
  experience: [{ ...EMPTY_EXPERIENCE }],
  projects: [{ ...EMPTY_PROJECT }],
  certifications: [{ ...EMPTY_CERTIFICATION }],
  achievements: [],
  keywords: [],
});

function normalizeResumeForEditor(resume, candidate) {
  if (!resume) {
    return createEmptyResume(candidate);
  }

  const resumeData = resume.resumeData || {};
  return {
    title: resume.title || createEmptyResume(candidate).title,
    templateId: resume.templateId || "",
    personalInfo: {
      ...createEmptyResume(candidate).personalInfo,
      ...(resumeData.personalInfo || {}),
    },
    professionalSummary: resumeData.professionalSummary || "",
    skills: Array.isArray(resumeData.skills) ? resumeData.skills : [],
    education: Array.isArray(resumeData.education) && resumeData.education.length ? resumeData.education : [{ ...EMPTY_ENTRY }],
    experience: Array.isArray(resumeData.experience) && resumeData.experience.length ? resumeData.experience : [{ ...EMPTY_EXPERIENCE }],
    projects: Array.isArray(resumeData.projects) && resumeData.projects.length ? resumeData.projects : [{ ...EMPTY_PROJECT }],
    certifications: Array.isArray(resumeData.certifications) && resumeData.certifications.length
      ? resumeData.certifications
      : [{ ...EMPTY_CERTIFICATION }],
    achievements: Array.isArray(resumeData.achievements) ? resumeData.achievements : [],
    keywords: Array.isArray(resumeData.keywords) ? resumeData.keywords : [],
  };
}

function TagInput({ label, values, onAdd, onRemove, placeholder }) {
  const [input, setInput] = useState("");

  const commit = () => {
    const trimmed = input.trim();
    if (!trimmed) return;
    onAdd(trimmed);
    setInput("");
  };

  return (
    <div className="resume-form-block">
      <label>{label}</label>
      <div className="resume-tag-shell">
        <div className="resume-tag-list">
          {values.map((value) => (
            <span key={value} className="resume-tag-pill">
              {value}
              <button type="button" onClick={() => onRemove(value)} aria-label={`Remove ${value}`}>
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
        <input
          type="text"
          value={input}
          placeholder={placeholder}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === ",") {
              event.preventDefault();
              commit();
            }
          }}
          onBlur={commit}
        />
      </div>
    </div>
  );
}

function RepeaterSection({ title, items, fields, onChange, onAdd, onRemove, addLabel }) {
  return (
    <section className="resume-form-panel">
      <div className="resume-section-header">
        <div>
          <h4>{title}</h4>
        </div>
        <button type="button" className="resume-outline-btn" onClick={onAdd}>
          <Plus size={15} />
          {addLabel}
        </button>
      </div>

      <div className="resume-repeater-list">
        {items.map((item, index) => (
          <article key={`${title}-${index}`} className="resume-repeater-card">
            <div className="resume-repeater-grid">
              {fields.map((field) => (
                <label key={field.key} className={field.type === "textarea" ? "full-width" : ""}>
                  <span>{field.label}</span>
                  {field.type === "textarea" ? (
                    <textarea
                      rows={4}
                      value={item[field.key] || ""}
                      onChange={(event) => onChange(index, field.key, event.target.value)}
                    />
                  ) : (
                    <input
                      type="text"
                      value={item[field.key] || ""}
                      onChange={(event) => onChange(index, field.key, event.target.value)}
                    />
                  )}
                </label>
              ))}
            </div>
            {items.length > 1 ? (
              <button type="button" className="resume-delete-link" onClick={() => onRemove(index)}>
                <Trash2 size={14} />
                Remove
              </button>
            ) : null}
          </article>
        ))}
      </div>
    </section>
  );
}

export default function CandidateResumeModule({ candidate, showAlert, onCandidateUpdate }) {
  const [resumes, setResumes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [builderOpen, setBuilderOpen] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState("");
  const [editingResumeId, setEditingResumeId] = useState(null);
  const [formState, setFormState] = useState(() => createEmptyResume(candidate));
  const [saving, setSaving] = useState(false);
  const [atsResult, setAtsResult] = useState(null);
  const [profileResumeFile, setProfileResumeFile] = useState(null);
  const [uploadingProfileResume, setUploadingProfileResume] = useState(false);
  const [latestSavedResume, setLatestSavedResume] = useState(null);

  // FIX: Track per-resume loading states so buttons show feedback during blob fetch.
  const [pdfLoading, setPdfLoading] = useState({});

  const loadResumes = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await fetchResumes();
      setResumes(data);
    } catch (err) {
      setError(err.response?.data?.error || "Unable to load your resumes right now.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadResumes();
  }, []);

  useEffect(() => {
    setFormState((current) => ({
      ...current,
      personalInfo: {
        ...current.personalInfo,
        name: current.personalInfo.name || candidate?.fullName || "",
        email: current.personalInfo.email || candidate?.email || "",
        phone: current.personalInfo.phone || candidate?.phoneNumber || "",
        location: current.personalInfo.location || [candidate?.city, candidate?.state].filter(Boolean).join(", "),
      },
    }));
  }, [candidate]);

  const template = useMemo(
    () => RESUME_TEMPLATES.find((item) => item.id === (selectedTemplateId || formState.templateId)) || RESUME_TEMPLATES[0],
    [selectedTemplateId, formState.templateId]
  );

  const computedCompleteness = useMemo(() => {
    let total = 9;
    let done = 0;
    const personal = formState.personalInfo;
    if (personal.name && personal.email && personal.phone && personal.location) done += 1;
    if (formState.professionalSummary.trim()) done += 1;
    if (formState.skills.length) done += 1;
    if (formState.education.some((item) => Object.values(item).some(Boolean))) done += 1;
    if (formState.experience.some((item) => Object.values(item).some(Boolean))) done += 1;
    if (formState.projects.some((item) => Object.values(item).some(Boolean))) done += 1;
    if (formState.certifications.some((item) => Object.values(item).some(Boolean))) done += 1;
    if (formState.achievements.length) done += 1;
    if (formState.keywords.length) done += 1;
    return Math.round((done / total) * 100);
  }, [formState]);

  const openBuilderForCreate = () => {
    const nextState = createEmptyResume(candidate);
    setEditingResumeId(null);
    setSelectedTemplateId("");
    setFormState(nextState);
    setAtsResult(null);
    setLatestSavedResume(null);
    setBuilderOpen(true);
  };

  const openBuilderForEdit = (resume) => {
    const nextState = normalizeResumeForEditor(resume, candidate);
    setEditingResumeId(resume.id);
    setSelectedTemplateId(resume.templateId);
    setFormState(nextState);
    setAtsResult({
      score: resume.atsScore || 0,
      missingKeywords: resume.missingKeywords || [],
      suggestions: resume.suggestions || [],
    });
    setLatestSavedResume(resume);
    setBuilderOpen(true);
  };

  const closeBuilder = () => {
    setBuilderOpen(false);
  };

  const updatePersonalInfo = (key, value) => {
    setFormState((current) => ({
      ...current,
      personalInfo: {
        ...current.personalInfo,
        [key]: value,
      },
    }));
  };

  const updateListSection = (section, index, key, value) => {
    setFormState((current) => ({
      ...current,
      [section]: current[section].map((item, itemIndex) =>
        itemIndex === index ? { ...item, [key]: value } : item
      ),
    }));
  };

  const addListSectionItem = (section, factory) => {
    setFormState((current) => ({
      ...current,
      [section]: [...current[section], factory()],
    }));
  };

  const removeListSectionItem = (section, index) => {
    setFormState((current) => ({
      ...current,
      [section]: current[section].filter((_, itemIndex) => itemIndex !== index),
    }));
  };

  const addTag = (section, value) => {
    setFormState((current) => ({
      ...current,
      [section]: current[section].includes(value) ? current[section] : [...current[section], value],
    }));
  };

  const removeTag = (section, value) => {
    setFormState((current) => ({
      ...current,
      [section]: current[section].filter((item) => item !== value),
    }));
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      setError("");

      const payload = {
        ...formState,
        templateId: selectedTemplateId || formState.templateId || RESUME_TEMPLATES[0].id,
      };

      const savedResume = editingResumeId
        ? await updateResume(editingResumeId, payload)
        : await createResume(payload);

      setFormState(normalizeResumeForEditor(savedResume, candidate));
      setSelectedTemplateId(savedResume.templateId);
      setEditingResumeId(savedResume.id);
      setLatestSavedResume(savedResume);
      setAtsResult({
        score: savedResume.atsScore || 0,
        missingKeywords: savedResume.missingKeywords || [],
        suggestions: savedResume.suggestions || [],
      });

      await loadResumes();
      if (showAlert) {
        await showAlert({
          title: editingResumeId ? "Resume Updated" : "Resume Saved",
          message: "Your ATS-friendly resume has been saved and the PDF is ready.",
          tone: "success",
        });
      }
    } catch (err) {
      setError(err.response?.data?.error || "Unable to save your resume right now.");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (resume) => {
    const confirmed = window.confirm(`Delete "${resume.title}"? This cannot be undone.`);
    if (!confirmed) return;

    try {
      await deleteResume(resume.id);
      if (editingResumeId === resume.id) {
        setBuilderOpen(false);
      }
      await loadResumes();
    } catch (err) {
      setError(err.response?.data?.error || "Unable to delete the selected resume.");
    }
  };

  // FIX: View PDF — fetch blob with session cookie, open in new tab via object URL.
  // Plain <a href> without credentials was causing a 401 from the backend.
  const handleViewPdf = async (resumeId) => {
    setPdfLoading((prev) => ({ ...prev, [`view-${resumeId}`]: true }));
    try {
      const blob = await fetchResumePdfBlob(resumeId, "inline");
      const objectUrl = URL.createObjectURL(blob);
      window.open(objectUrl, "_blank");
      // Revoke after a short delay to allow the tab to load the content
      setTimeout(() => URL.revokeObjectURL(objectUrl), 10000);
    } catch (err) {
      setError("Unable to open the resume PDF. Please try again.");
    } finally {
      setPdfLoading((prev) => ({ ...prev, [`view-${resumeId}`]: false }));
    }
  };

  // FIX: Download PDF — fetch blob with session cookie, trigger download via <a> click.
  const handleDownloadPdf = async (resumeId, title) => {
    setPdfLoading((prev) => ({ ...prev, [`dl-${resumeId}`]: true }));
    try {
      const blob = await fetchResumePdfBlob(resumeId, "attachment");
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = `${title || "resume"}.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(objectUrl);
    } catch (err) {
      setError("Unable to download the resume PDF. Please try again.");
    } finally {
      setPdfLoading((prev) => ({ ...prev, [`dl-${resumeId}`]: false }));
    }
  };

  const profileResumeUrl = getCandidateFileUrl(candidate?.resumePath);
  const profileResumeName = getResumeFileName(candidate?.resumePath);

  const handleProfileResumeUpload = async () => {
    if (!profileResumeFile || !candidate) {
      return;
    }

    try {
      setUploadingProfileResume(true);
      setError("");
      const updatedCandidate = await updateCandidateProfile(candidate, { resumeFile: profileResumeFile });
      setProfileResumeFile(null);
      onCandidateUpdate?.(updatedCandidate);
      await showAlert?.({
        title: "Resume Updated",
        message: "Your uploaded profile resume is now available to view and download.",
        tone: "success",
      });
    } catch (err) {
      setError(err.response?.data?.error || "Unable to upload the selected resume.");
    } finally {
      setUploadingProfileResume(false);
    }
  };

  return (
    <div className="resume-module-stack">
      <section className="candidate-panel resume-module-hero">
        <div>
          <p className="resume-module-eyebrow">Resume Studio</p>
          <h3>Build ATS-friendly resumes without affecting your existing profile upload.</h3>
          <p>
            Create multiple tailored resumes, compare templates, preview changes live, and download recruiter-ready PDFs.
          </p>
        </div>
        <button type="button" className="candidate-primary-btn candidate-primary-btn-inline" onClick={openBuilderForCreate}>
          <FilePlus2 size={17} />
          Create Resume
        </button>
      </section>

      {error ? <div className="candidate-alert">{error}</div> : null}

      <section className="candidate-panel resume-upload-panel">
        <div className="candidate-panel-header">
          <div>
            <h3>Uploaded Profile Resume</h3>
            <p>View, download, or replace the resume currently saved in your candidate profile.</p>
          </div>
          <span className="resume-library-count">{profileResumeUrl ? "Available" : "Not Uploaded"}</span>
        </div>

        <div className="resume-upload-grid">
          <div className="resume-upload-card">
            <div>
              <p className="resume-upload-label">Current file</p>
              <h4>{profileResumeName || "No resume uploaded yet"}</h4>
              <p className="resume-upload-help">
                {profileResumeUrl
                  ? "This is the resume recruiters will see from your profile."
                  : "Upload a PDF or document resume to make it available from your profile and dashboard."}
              </p>
            </div>

            {/* Profile resume uses a normal <a> tag because getCandidateFileUrl
                now encodes the filename — no auth cookie needed for this endpoint. */}
            <div className="resume-card-actions">
              {profileResumeUrl ? (
                <>
                  <a href={profileResumeUrl} target="_blank" rel="noreferrer" className="resume-action-btn">
                    <Eye size={15} />
                    View Resume
                  </a>
                  <a href={profileResumeUrl} download={profileResumeName} className="resume-action-btn">
                    <Download size={15} />
                    Download Resume
                  </a>
                </>
              ) : null}
            </div>
          </div>

          <div className="resume-upload-card">
            <label className="resume-file-picker">
              <span>Choose Resume File</span>
              <input
                type="file"
                accept=".pdf,.doc,.docx"
                onChange={(event) => setProfileResumeFile(event.target.files?.[0] || null)}
              />
            </label>
            <p className="resume-upload-help">
              {profileResumeFile ? `Selected: ${profileResumeFile.name}` : "Supported formats: PDF, DOC, DOCX"}
            </p>
            <div className="resume-card-actions">
              <button
                type="button"
                className="resume-action-btn"
                onClick={handleProfileResumeUpload}
                disabled={!profileResumeFile || uploadingProfileResume}
              >
                <FilePlus2 size={15} />
                {uploadingProfileResume ? "Uploading..." : profileResumeUrl ? "Replace Resume" : "Upload Resume"}
              </button>
            </div>
          </div>
        </div>

        {profileResumeUrl && isPdfResume(candidate?.resumePath) ? (
          <div className="resume-embedded-preview">
            <iframe src={profileResumeUrl} title="Uploaded profile resume preview" className="resume-embedded-frame" />
          </div>
        ) : null}
      </section>

      <section className="candidate-panel">
        <div className="candidate-panel-header">
          <div>
            <h3>Resume List</h3>
            <p>View, download, update, or remove any generated resume.</p>
          </div>
          <span className="resume-library-count">{resumes.length} Saved</span>
        </div>

        {loading ? (
          <div className="candidate-empty-state">
            <p>Loading your resume library...</p>
          </div>
        ) : resumes.length === 0 ? (
          <div className="candidate-empty-state">
            <p>No generated resumes yet. Start with a template to create your first one.</p>
          </div>
        ) : (
          <div className="resume-library-grid">
            {resumes.map((resume) => {
              const resumeTemplate = RESUME_TEMPLATES.find((item) => item.id === resume.templateId) || RESUME_TEMPLATES[0];
              return (
                <article key={resume.id} className="resume-library-card">
                  <div className="resume-card-head">
                    <div className="resume-card-swatch" style={{ background: resumeTemplate.accent }} />
                    <div>
                      <h4>{resume.title}</h4>
                      <p>{resumeTemplate.name}</p>
                    </div>
                    <span className="resume-score-chip">{resume.atsScore || 0}/100</span>
                  </div>

                  <div className="resume-card-meta">
                    <span>Updated {new Date(resume.updatedAt).toLocaleDateString()}</span>
                    <span>{(resume.resumeData?.skills || []).length} skills</span>
                  </div>

                  {/* FIX: Replaced <a href> with buttons that fetch the PDF blob
                      through axios (with session cookie). The old anchor approach
                      hit the backend without auth and received a 401. */}
                  <div className="resume-card-actions">
                    <button
                      type="button"
                      className="resume-action-btn"
                      onClick={() => handleViewPdf(resume.id)}
                      disabled={pdfLoading[`view-${resume.id}`]}
                    >
                      <Eye size={15} />
                      {pdfLoading[`view-${resume.id}`] ? "Opening..." : "View Resume"}
                    </button>
                    <button
                      type="button"
                      className="resume-action-btn"
                      onClick={() => handleDownloadPdf(resume.id, resume.title)}
                      disabled={pdfLoading[`dl-${resume.id}`]}
                    >
                      <Download size={15} />
                      {pdfLoading[`dl-${resume.id}`] ? "Downloading..." : "Download Resume"}
                    </button>
                    <button type="button" className="resume-action-btn" onClick={() => openBuilderForEdit(resume)}>
                      <PencilLine size={15} />
                      Edit Resume
                    </button>
                    <button type="button" className="resume-action-btn danger" onClick={() => handleDelete(resume)}>
                      <Trash2 size={14} />
                      Delete Resume
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="candidate-panel">
        <div className="candidate-panel-header">
          <div>
            <h3>Create New Resume</h3>
            <p>Choose one of the ATS-friendly templates to start building.</p>
          </div>
        </div>

        <div className="resume-template-grid">
          {RESUME_TEMPLATES.map((item) => {
            const selected = (selectedTemplateId || formState.templateId) === item.id;
            return (
              <button
                type="button"
                key={item.id}
                className={`resume-template-card ${selected ? "selected" : ""}`}
                onClick={() => {
                  setSelectedTemplateId(item.id);
                  setFormState((current) => ({ ...current, templateId: item.id }));
                  setBuilderOpen(true);
                }}
              >
                <div className="resume-template-preview" style={{ background: item.accent }}>
                  <ResumeTemplateMiniPreview templateId={item.id} />
                </div>
                <div className="resume-template-copy">
                  <div className="resume-template-title-row">
                    <h4>{item.name}</h4>
                    {selected ? <CheckCircle2 size={16} /> : null}
                  </div>
                  <p>{item.summary}</p>
                  <div className="resume-template-tags">
                    {item.labels.map((label) => (
                      <span key={label}>{label}</span>
                    ))}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </section>

      {builderOpen ? (
        <section className="candidate-panel resume-builder-shell">
          <div className="candidate-panel-header resume-builder-header">
            <div>
              <h3>{editingResumeId ? "Update Resume" : "Resume Builder Form"}</h3>
              <p>Fill ATS-friendly fields and review the live preview before saving.</p>
            </div>
            <div className="resume-builder-actions-top">
              <span className="resume-completeness-chip">{computedCompleteness}% complete</span>
              <button type="button" className="resume-outline-btn" onClick={closeBuilder}>
                Edit Later
              </button>
            </div>
          </div>

          <div className="resume-builder-grid">
            <div className="resume-form-column">
              <section className="resume-form-panel">
                <div className="resume-section-header">
                  <h4>Resume Basics</h4>
                </div>
                <div className="resume-form-grid">
                  <label>
                    <span>Resume Title</span>
                    <input
                      type="text"
                      value={formState.title}
                      onChange={(event) => setFormState((current) => ({ ...current, title: event.target.value }))}
                    />
                  </label>
                </div>
              </section>

              <section className="resume-form-panel">
                <div className="resume-section-header">
                  <h4>Personal Info</h4>
                </div>
                <div className="resume-form-grid">
                  {[
                    ["name", "Name"],
                    ["email", "Email"],
                    ["phone", "Phone"],
                    ["location", "Location"],
                    ["linkedin", "LinkedIn"],
                    ["portfolio", "Portfolio"],
                  ].map(([key, label]) => (
                    <label key={key}>
                      <span>{label}</span>
                      <input
                        type="text"
                        value={formState.personalInfo[key]}
                        onChange={(event) => updatePersonalInfo(key, event.target.value)}
                      />
                    </label>
                  ))}
                </div>
              </section>

              <section className="resume-form-panel">
                <div className="resume-section-header">
                  <h4>Professional Summary</h4>
                </div>
                <label>
                  <textarea
                    rows={5}
                    value={formState.professionalSummary}
                    onChange={(event) => setFormState((current) => ({ ...current, professionalSummary: event.target.value }))}
                    placeholder="Write a concise summary aligned to your target role."
                  />
                </label>
              </section>

              <TagInput
                label="Skills"
                values={formState.skills}
                onAdd={(value) => addTag("skills", value)}
                onRemove={(value) => removeTag("skills", value)}
                placeholder="Add a skill and press Enter"
              />

              <RepeaterSection
                title="Education"
                items={formState.education}
                fields={[
                  { key: "institution", label: "Institution" },
                  { key: "degree", label: "Degree" },
                  { key: "duration", label: "Duration" },
                  { key: "description", label: "Description", type: "textarea" },
                ]}
                onChange={(index, key, value) => updateListSection("education", index, key, value)}
                onAdd={() => addListSectionItem("education", () => ({ ...EMPTY_ENTRY }))}
                onRemove={(index) => removeListSectionItem("education", index)}
                addLabel="Add Education"
              />

              <RepeaterSection
                title="Experience"
                items={formState.experience}
                fields={[
                  { key: "company", label: "Company" },
                  { key: "role", label: "Role" },
                  { key: "duration", label: "Duration" },
                  { key: "description", label: "Description", type: "textarea" },
                ]}
                onChange={(index, key, value) => updateListSection("experience", index, key, value)}
                onAdd={() => addListSectionItem("experience", () => ({ ...EMPTY_EXPERIENCE }))}
                onRemove={(index) => removeListSectionItem("experience", index)}
                addLabel="Add Experience"
              />

              <RepeaterSection
                title="Projects"
                items={formState.projects}
                fields={[
                  { key: "name", label: "Project Name" },
                  { key: "role", label: "Role" },
                  { key: "duration", label: "Duration" },
                  { key: "description", label: "Description", type: "textarea" },
                ]}
                onChange={(index, key, value) => updateListSection("projects", index, key, value)}
                onAdd={() => addListSectionItem("projects", () => ({ ...EMPTY_PROJECT }))}
                onRemove={(index) => removeListSectionItem("projects", index)}
                addLabel="Add Project"
              />

              <RepeaterSection
                title="Certifications"
                items={formState.certifications}
                fields={[
                  { key: "name", label: "Certification" },
                  { key: "issuer", label: "Issuer" },
                  { key: "year", label: "Year" },
                  { key: "description", label: "Description", type: "textarea" },
                ]}
                onChange={(index, key, value) => updateListSection("certifications", index, key, value)}
                onAdd={() => addListSectionItem("certifications", () => ({ ...EMPTY_CERTIFICATION }))}
                onRemove={(index) => removeListSectionItem("certifications", index)}
                addLabel="Add Certification"
              />

              <TagInput
                label="Achievements"
                values={formState.achievements}
                onAdd={(value) => addTag("achievements", value)}
                onRemove={(value) => removeTag("achievements", value)}
                placeholder="Add an achievement"
              />

              <TagInput
                label="Keywords"
                values={formState.keywords}
                onAdd={(value) => addTag("keywords", value)}
                onRemove={(value) => removeTag("keywords", value)}
                placeholder="Add ATS keywords"
              />

              <div className="resume-submit-row">
                <button type="button" className="candidate-primary-btn candidate-primary-btn-inline" onClick={handleSave} disabled={saving}>
                  <Sparkles size={16} />
                  {saving ? "Saving..." : editingResumeId ? "Update existing resume" : "Save Resume"}
                </button>
                {/* FIX: Replaced <a href download> with a button using handleDownloadPdf
                    so the PDF is fetched with the session cookie attached. */}
                {editingResumeId ? (
                  <button
                    type="button"
                    className="candidate-secondary-btn"
                    onClick={() => handleDownloadPdf(latestSavedResume?.id || editingResumeId, latestSavedResume?.title || formState.title)}
                    disabled={pdfLoading[`dl-${editingResumeId}`]}
                  >
                    <Download size={16} />
                    {pdfLoading[`dl-${editingResumeId}`] ? "Downloading..." : "Download as PDF"}
                  </button>
                ) : null}
              </div>
            </div>

            <div className="resume-preview-column">
              <section className="resume-live-preview-card">
                <div className="resume-live-preview-head">
                  <div>
                    <p>Live Preview</p>
                    <strong>{template.name}</strong>
                  </div>
                  <span className="resume-template-mini-chip">{selectedTemplateId || formState.templateId || template.id}</span>
                </div>

                <ResumeTemplateSheet templateId={template.id} formState={formState} />
              </section>

              <section className="resume-ats-card">
                <div className="resume-ats-head">
                  <div>
                    <p>ATS Score Checker</p>
                    <strong>{atsResult ? "Latest generated analysis" : "Available after save"}</strong>
                  </div>
                  <span className="resume-ats-ring">{atsResult?.score ?? "--"}</span>
                </div>

                <div className="resume-ats-body">
                  <div className="resume-ats-metric">
                    <span>Draft completeness</span>
                    <strong>{computedCompleteness}%</strong>
                  </div>
                  <div className="resume-ats-metric">
                    <span>Keywords added</span>
                    <strong>{formState.keywords.length}</strong>
                  </div>

                  <div>
                    <h4>Missing keywords</h4>
                    {atsResult?.missingKeywords?.length ? (
                      <div className="resume-preview-chip-row">
                        {atsResult.missingKeywords.map((keyword) => (
                          <span key={keyword}>{keyword}</span>
                        ))}
                      </div>
                    ) : (
                      <p className="resume-ats-muted">
                        {atsResult ? "No missing keywords detected from your provided list." : "Save the resume to run ATS keyword analysis."}
                      </p>
                    )}
                  </div>

                  <div>
                    <h4>Suggestions to improve</h4>
                    {atsResult?.suggestions?.length ? (
                      <ul className="resume-ats-list">
                        {atsResult.suggestions.map((suggestion) => (
                          <li key={suggestion}>{suggestion}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="resume-ats-muted">Suggestions will appear after generation.</p>
                    )}
                  </div>
                </div>
              </section>
            </div>
          </div>
        </section>
      ) : null}
    </div>
  );
}
