import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  CalendarDays,
  Download,
  FileText,
  GraduationCap,
  Mail,
  MapPin,
  PencilLine,
  Phone,
  UserRound,
} from "lucide-react";
import { fetchCandidateProfile } from "./profileApi";
import {
  DEFAULT_PROFILE_IMAGE,
  formatDisplayValue,
  getCandidateFileUrl,
  getResumeFileName,
  getSkillList,
  isPdfResume,
  normalizeCandidate,
} from "./profileUtils";
import { API_BASE_URL } from "../../../config";
import "./CandidateProfile.css";

export default function CandidateProfileView() {
  const navigate = useNavigate();
  const [candidate, setCandidate] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profile = await fetchCandidateProfile();
        const normalizedProfile = normalizeCandidate(profile);
        setCandidate(normalizedProfile);
        localStorage.setItem("candidate", JSON.stringify(profile));
        window.dispatchEvent(new Event("auth-change"));
      } catch (err) {
        const message =
          err.response?.data?.error ||
          "We could not load your full profile right now.";
        setError(message);

        if (err.response?.status === 401) {
          localStorage.removeItem("candidate");
          navigate("/candidate/login");
        }
      } finally {
        setLoading(false);
      }
    };

    loadProfile();
  }, [navigate]);

  if (loading) {
    return (
      <div className="candidate-profile-shell">
        <div className="candidate-profile-loading">
          Loading your full profile...
        </div>
      </div>
    );
  }

  if (!candidate) {
    return (
      <div className="candidate-profile-shell">
        <div className="candidate-profile-feedback error">{error}</div>
      </div>
    );
  }

  const profileImage =
    getCandidateFileUrl(candidate.profilePic) || DEFAULT_PROFILE_IMAGE;
  const resumeUrl = candidate.resumePath
    ? `${API_BASE_URL}/candidates/me/resume?disposition=inline`
    : "";
  const resumeDownloadUrl = candidate.resumePath
    ? `${API_BASE_URL}/candidates/me/resume?disposition=attachment`
    : "";
  const resumeName = getResumeFileName(candidate.resumePath);
  const skills = getSkillList(candidate.skills);

  const detailItems = [
    {
      label: "Full Name",
      value: candidate.fullName,
      icon: <UserRound size={18} />,
    },
    { label: "Email", value: candidate.email, icon: <Mail size={18} /> },
    { label: "Phone", value: candidate.phoneNumber, icon: <Phone size={18} /> },
    {
      label: "Alternate Phone",
      value: candidate.alternatePhoneNumber,
      icon: <Phone size={18} />,
    },
    { label: "Gender", value: candidate.gender, icon: <UserRound size={18} /> },
    {
      label: "Date of Birth",
      value: candidate.dateOfBirth,
      icon: <CalendarDays size={18} />,
    },
    {
      label: "City, State",
      value:
        candidate.city || candidate.state
          ? [candidate.city, candidate.state].filter(Boolean).join(", ")
          : "",
      icon: <MapPin size={18} />,
    },
    {
      label: "Education",
      value: candidate.highestEducation,
      icon: <GraduationCap size={18} />,
    },
    {
      label: "College / University",
      value: candidate.collegeUniversity,
      icon: <GraduationCap size={18} />,
    },
    {
      label: "Graduation Year",
      value: candidate.yearOfGraduation,
      icon: <GraduationCap size={18} />,
    },
    {
      label: "Experience",
      value:
        candidate.experience === "" || candidate.experience === null
          ? ""
          : `${candidate.experience} years`,
      icon: <FileText size={18} />,
    },
  ];

  return (
    <div className="candidate-profile-shell">
      <div className="candidate-profile-page">
        <div className="candidate-profile-topbar">
          <div className="candidate-profile-heading-block">
            <button
              type="button"
              className="candidate-profile-link-btn secondary"
              onClick={() => navigate(-1)}
            >
              <ArrowLeft size={18} />
              Back
            </button>
            <p className="candidate-profile-eyebrow">Candidate Profile</p>
            <p className="candidate-profile-subtitle">
              Review the complete information you submitted during registration.
            </p>
          </div>

          <div className="candidate-profile-actions">
            <button
              type="button"
              className="candidate-profile-link-btn primary"
              onClick={() => navigate("/candidate/profile/edit")}
            >
              <PencilLine size={18} />
              Edit Profile
            </button>
          </div>
        </div>

        {error && (
          <div className="candidate-profile-feedback error">{error}</div>
        )}

        <section className="candidate-profile-hero-card">
          <div className="candidate-profile-identity">
            <img
              src={profileImage}
              alt={candidate.fullName || "Candidate profile"}
              className="candidate-profile-avatar"
              onError={(event) => {
                event.currentTarget.src = DEFAULT_PROFILE_IMAGE;
              }}
            />

            <div>
              <h2>{formatDisplayValue(candidate.fullName)}</h2>
              <p>{formatDisplayValue(candidate.email)}</p>
              <span className="candidate-profile-highlight">
                {skills.length
                  ? `${skills.length} skill${skills.length > 1 ? "s" : ""}`
                  : "Skills not added"}
              </span>
            </div>
          </div>

          <div className="candidate-profile-resume-card">
            <div>
              <p className="candidate-profile-card-label">Resume</p>
              <strong>{resumeName || "No resume uploaded"}</strong>
            </div>

            {resumeUrl ? (
              <div className="candidate-profile-inline-actions">
                <a
                  href={resumeUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="candidate-profile-link-btn secondary"
                >
                  View Resume
                </a>
                <a
                  href={resumeDownloadUrl}
                  download={resumeName}
                  className="candidate-profile-link-btn secondary"
                >
                  <Download size={16} />
                  Download
                </a>
              </div>
            ) : (
              <p className="candidate-profile-muted">
                Upload a resume from the edit page.
              </p>
            )}
          </div>
        </section>

        <section className="candidate-profile-section">
          <div className="candidate-profile-section-header">
            <h3>Personal and Professional Details</h3>
          </div>

          <div className="candidate-profile-detail-grid">
            {detailItems.map((item) => (
              <article
                key={item.label}
                className="candidate-profile-detail-card"
              >
                <div className="candidate-profile-detail-icon">{item.icon}</div>
                <div>
                  <span>{item.label}</span>
                  <strong>{formatDisplayValue(item.value)}</strong>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="candidate-profile-section">
          <div className="candidate-profile-section-header">
            <h3>Skills</h3>
          </div>

          <div className="candidate-profile-skill-list">
            {skills.length ? (
              skills.map((skill) => (
                <span key={skill} className="candidate-profile-skill-pill">
                  {skill}
                </span>
              ))
            ) : (
              <p className="candidate-profile-muted">
                No skills have been added yet.
              </p>
            )}
          </div>
        </section>

        {resumeUrl && (
          <section className="candidate-profile-section">
            <div className="candidate-profile-section-header">
              <h3>Resume Preview</h3>
            </div>

            {isPdfResume(candidate.resumePath) ? (
              <iframe
                src={resumeUrl}
                title="Candidate resume preview"
                className="candidate-profile-resume-preview"
              />
            ) : (
              <div className="candidate-profile-feedback">
                This resume format does not support inline preview here. Use the
                View Resume button to open it.
              </div>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
