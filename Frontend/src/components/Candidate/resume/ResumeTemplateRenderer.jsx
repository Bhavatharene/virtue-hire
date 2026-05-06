import React from "react";

const SECTION_ITEMS = {
  experience: {
    empty: "Add experience details to preview them here.",
    titleKeys: ["role", "company"],
    metaKey: "duration",
  },
  education: {
    empty: "Add education details to preview them here.",
    titleKeys: ["degree", "institution"],
    metaKey: "duration",
  },
  projects: {
    empty: "Add project details to preview them here.",
    titleKeys: ["name", "role"],
    metaKey: "duration",
  },
  certifications: {
    empty: "Add certification details to preview them here.",
    titleKeys: ["name", "issuer"],
    metaKey: "year",
  },
};

function cleanList(values) {
  return Array.isArray(values) ? values.filter(Boolean) : [];
}

function hasItemContent(item) {
  return item && Object.values(item).some(Boolean);
}

function getItems(formState, section) {
  return cleanList(formState[section]).filter(hasItemContent);
}

function getTitle(item, section) {
  return SECTION_ITEMS[section].titleKeys.map((key) => item[key]).filter(Boolean).join(" | ");
}

function getProfessionalTitle(formState) {
  const firstRole = getItems(formState, "experience").map((item) => item.role).find(Boolean);
  return firstRole || formState.title || "Professional Resume";
}

function ContactLine({ personalInfo, separator = " | " }) {
  const items = [
    personalInfo.email,
    personalInfo.phone,
    personalInfo.location,
    personalInfo.linkedin,
    personalInfo.portfolio,
  ].filter(Boolean);
  return <p>{items.length ? items.join(separator) : "Email | Phone | Location"}</p>;
}

function Section({ title, children, className = "" }) {
  return (
    <section className={`ats-template-section ${className}`}>
      <h4>{title}</h4>
      {children}
    </section>
  );
}

function TextSection({ title, value, fallback }) {
  return (
    <Section title={title}>
      <p>{value || fallback}</p>
    </Section>
  );
}

function Skills({ skills }) {
  return (
    <Section title="Skills">
      {skills.length ? (
        <div className="ats-template-skill-list">
          {skills.map((skill) => (
            <span key={skill}>{skill}</span>
          ))}
        </div>
      ) : (
        <p>Add skills to preview ATS keywords.</p>
      )}
    </Section>
  );
}

function ItemSection({ title, section, formState }) {
  const items = getItems(formState, section);
  return (
    <Section title={title}>
      {items.length ? (
        items.map((item, index) => (
          <article key={`${section}-${index}`} className="ats-template-item">
            <div className="ats-template-item-head">
              <strong>{getTitle(item, section) || title}</strong>
              <span>{item[SECTION_ITEMS[section].metaKey] || ""}</span>
            </div>
            {item.description ? <p>{item.description}</p> : null}
          </article>
        ))
      ) : (
        <p>{SECTION_ITEMS[section].empty}</p>
      )}
    </Section>
  );
}

function Achievements({ achievements }) {
  return (
    <Section title="Achievements">
      {achievements.length ? (
        <ul>
          {achievements.map((achievement) => (
            <li key={achievement}>{achievement}</li>
          ))}
        </ul>
      ) : (
        <p>Add achievements to strengthen the final resume.</p>
      )}
    </Section>
  );
}

function TemplateBody({ formState, includeCertifications = true, includeProjects = true }) {
  return (
    <>
      <TextSection
        title="Professional Summary"
        value={formState.professionalSummary}
        fallback="Your summary will appear here as you type."
      />
      <Skills skills={cleanList(formState.skills)} />
      <ItemSection title="Experience" section="experience" formState={formState} />
      <ItemSection title="Education" section="education" formState={formState} />
      {includeProjects ? <ItemSection title="Projects" section="projects" formState={formState} /> : null}
      {includeCertifications ? <ItemSection title="Certifications" section="certifications" formState={formState} /> : null}
      <Achievements achievements={cleanList(formState.achievements)} />
    </>
  );
}

function ClassicProfessional({ formState }) {
  const personalInfo = formState.personalInfo;
  return (
    <div className="ats-template ats-template-classic">
      <header className="ats-template-centered-header">
        <h2>{personalInfo.name || "Your Name"}</h2>
        <strong>{getProfessionalTitle(formState)}</strong>
        <ContactLine personalInfo={personalInfo} />
      </header>
      <TemplateBody formState={formState} />
    </div>
  );
}

function ModernMinimal({ formState }) {
  const personalInfo = formState.personalInfo;
  return (
    <div className="ats-template ats-template-modern">
      <header>
        <h2>{personalInfo.name || "Your Name"}</h2>
        <strong>{getProfessionalTitle(formState)}</strong>
        <ContactLine personalInfo={personalInfo} separator=" / " />
      </header>
      <TemplateBody formState={formState} />
    </div>
  );
}

function TwoColumnExecutive({ formState }) {
  const personalInfo = formState.personalInfo;
  return (
    <div className="ats-template ats-template-executive">
      <aside>
        <Section title="Contact">
          <ContactLine personalInfo={personalInfo} />
        </Section>
        <Skills skills={cleanList(formState.skills)} />
        <ItemSection title="Certifications" section="certifications" formState={formState} />
      </aside>
      <main>
        <header>
          <h2>{personalInfo.name || "Your Name"}</h2>
          <strong>{getProfessionalTitle(formState)}</strong>
        </header>
        <TextSection
          title="Professional Summary"
          value={formState.professionalSummary}
          fallback="Your summary will appear here as you type."
        />
        <ItemSection title="Experience" section="experience" formState={formState} />
        <ItemSection title="Projects" section="projects" formState={formState} />
        <ItemSection title="Education" section="education" formState={formState} />
        <Achievements achievements={cleanList(formState.achievements)} />
      </main>
    </div>
  );
}

function CleanStructured({ formState }) {
  const personalInfo = formState.personalInfo;
  return (
    <div className="ats-template ats-template-structured">
      <header>
        <h2>{personalInfo.name || "Your Name"}</h2>
        <strong>{getProfessionalTitle(formState)}</strong>
        <ContactLine personalInfo={personalInfo} />
      </header>
      <TemplateBody formState={formState} />
    </div>
  );
}

function SimpleElegant({ formState }) {
  const personalInfo = formState.personalInfo;
  return (
    <div className="ats-template ats-template-elegant">
      <header className="ats-template-centered-header">
        <h2>{personalInfo.name || "Your Name"}</h2>
        <strong>{getProfessionalTitle(formState)}</strong>
        <ContactLine personalInfo={personalInfo} />
      </header>
      <TemplateBody formState={formState} />
    </div>
  );
}

const TEMPLATE_COMPONENTS = {
  classic: ClassicProfessional,
  modern: ModernMinimal,
  executive: TwoColumnExecutive,
  structured: CleanStructured,
  elegant: SimpleElegant,
};

export function ResumeTemplateSheet({ templateId, formState }) {
  const TemplateComponent = TEMPLATE_COMPONENTS[templateId] || ModernMinimal;
  return <TemplateComponent formState={formState} />;
}

export function ResumeTemplateMiniPreview({ templateId }) {
  return (
    <div className={`resume-template-mini-preview mini-${templateId}`}>
      <div className="mini-header">
        <span />
        <span />
      </div>
      <div className="mini-body">
        <span />
        <span />
        <span />
      </div>
      <div className="mini-grid">
        <span />
        <span />
      </div>
    </div>
  );
}
