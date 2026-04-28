import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../../services/api";
import AdminLayout from "./AdminLayout";
import {
  Users,
  UserCheck,
  Briefcase,
  TrendingUp,
  CheckCircle,
  ChevronRight,
  RefreshCw,
  Search,
  Plus
} from "lucide-react";
import "./AdminDashboard.css";

const EMPTY_DASHBOARD = {
  totalCandidates: 0,
  candidatesWithTest: 0,
  totalHrs: 0,
  totalRevenue: 0,
  unverifiedHrs: 0,
  pendingCandidates: 0,
  pendingAccessRequests: 0,
  pendingCombinedAssessmentRequests: 0,
  payments: [],
  candidates: [],
  combinedAssessmentNotifications: []
};

export default function AdminDashboard() {
  const [data, setData] = useState(EMPTY_DASHBOARD);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const API_BASE = "/admin";

  useEffect(() => {
    fetchDashboard();
  }, []);

  const fetchDashboard = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await api.get(`${API_BASE}/dashboard`);
      setData({
        ...EMPTY_DASHBOARD,
        ...(res.data || {}),
        payments: res.data?.payments || [],
        candidates: res.data?.candidates || []
      });
    } catch (e) {
      console.error("Dashboard Fetch Error:", e);
      setError(e.response?.data?.error || "Failed to load admin dashboard data.");
      setData(EMPTY_DASHBOARD);
    } finally {
      setLoading(false);
    }
  };

  const handleAddClick = () => {
    console.log("Add button clicked");
  };

  if (loading) {
    return (
      <div className="adm-loading-screen">
        <div className="adm-spinner"></div>
        <p>Initializing Admin Control Center...</p>
      </div>
    );
  }

  return (
    <AdminLayout
      description="Monitor platform growth, revenue, HR operations, and assessment activity from one control center."
      actions={
        <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          <button 
            onClick={handleAddClick} 
            className="adm-add-btn"
            title="Add new item"
          >
            <Plus size={18} />
          </button>
          <button onClick={fetchDashboard} className="adm-refresh-btn">
            <RefreshCw size={18} /> Refresh
          </button>
        </div>
      }
    >
      <div className="adm-dashboard-body">
        {error ? (
          <div className="adm-card" style={{ borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c" }}>
            {error}
          </div>
        ) : null}

        <section className="adm-stats-grid">
          <div className="adm-stat-card blue">
            <div className="adm-stat-icon">
              <Users size={18} />
            </div>
            <div className="adm-stat-content">
              <div className="adm-stat-value">{data.totalCandidates}</div>
              <div className="adm-stat-label">Total Candidates</div>
            </div>
          </div>

          <div className="adm-stat-card green">
            <div className="adm-stat-icon">
              <CheckCircle size={18} />
            </div>
            <div className="adm-stat-content">
              <div className="adm-stat-value">{data.candidatesWithTest}</div>
              <div className="adm-stat-label">Assessments Taken</div>
            </div>
          </div>

          <div className="adm-stat-card yellow">
            <div className="adm-stat-icon">
              <Briefcase size={18} />
            </div>
            <div className="adm-stat-content">
              <div className="adm-stat-value">{data.totalHrs}</div>
              <div className="adm-stat-label">Active HR Partners</div>
            </div>
          </div>

          <div className="adm-stat-card purple">
            <div className="adm-stat-icon">
              <TrendingUp size={18} />
            </div>
            <div className="adm-stat-content">
              <div className="adm-stat-value">Rs {data.totalRevenue?.toLocaleString() || "0"}</div>
              <div className="adm-stat-label">Gross Revenue</div>
            </div>
          </div>
        </section>

        <div className="adm-dashboard-grid">
          <div className="adm-column">
            <div className="adm-card queue-card">
              <div className="adm-card-header">
                <h3>Verification Queues</h3>
                <span className="adm-badge">
                  {data.unverifiedHrs + data.pendingCandidates + data.pendingAccessRequests + data.pendingCombinedAssessmentRequests} Tasks
                </span>
              </div>
              <div className="adm-card-body">
                <div className="adm-queue-item">
                  <div className="adm-queue-icon yellow">
                    <Briefcase size={18} />
                  </div>
                  <div className="adm-queue-details">
                    <div className="adm-queue-text">Pending HR Approvals</div>
                    <div className="adm-queue-sub">{data.unverifiedHrs} accounts waiting</div>
                  </div>
                  <Link to="/admin/hrs?filter=unverified" className="adm-action-link">
                    Review <ChevronRight size={16} />
                  </Link>
                </div>

                <div className="adm-queue-item">
                  <div className="adm-queue-icon red">
                    <UserCheck size={18} />
                  </div>
                  <div className="adm-queue-details">
                    <div className="adm-queue-text">Candidate Verification</div>
                    <div className="adm-queue-sub">{data.pendingCandidates} candidates waiting</div>
                  </div>
                  <Link to="/admin/candidates/pending" className="adm-action-link">
                    Review <ChevronRight size={16} />
                  </Link>
                </div>

                <div className="adm-queue-item">
                  <div className="adm-queue-icon red">
                    <RefreshCw size={18} />
                  </div>
                  <div className="adm-queue-details">
                    <div className="adm-queue-text">Candidate Access Requests</div>
                    <div className="adm-queue-sub">{data.pendingAccessRequests} requests waiting</div>
                  </div>
                  <Link to="/admin/access-requests" className="adm-action-link">
                    Review <ChevronRight size={16} />
                  </Link>
                </div>

                <div className="adm-queue-item">
                  <div className="adm-queue-icon yellow">
                    <Briefcase size={18} />
                  </div>
                  <div className="adm-queue-details">
                    <div className="adm-queue-text">Combined Assessment Requests</div>
                    <div className="adm-queue-sub">{data.pendingCombinedAssessmentRequests} requests waiting</div>
                  </div>
                  <span className="adm-action-link" style={{ cursor: "default" }}>
                    Create <ChevronRight size={16} />
                  </span>
                </div>
              </div>
            </div>

            <div className="adm-card payment-card mt-6">
              <div className="adm-card-header">
                <h3>Assessment Notifications</h3>
                <span className="adm-badge">{data.pendingCombinedAssessmentRequests} Open</span>
              </div>
              <div className="adm-card-body">
                {data.combinedAssessmentNotifications.length > 0 ? (
                  data.combinedAssessmentNotifications.map((item) => (
                    <div key={item.id} className="adm-payment-item">
                      <div className="adm-pay-info">
                        <div className="adm-pay-hr">{item.message}</div>
                        <div className="adm-pay-meta">
                          {item.createdAt ? new Date(item.createdAt).toLocaleString() : "Just now"}
                        </div>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="adm-empty-msg">No pending combined assessment notifications.</div>
                )}
              </div>
            </div>

            <div className="adm-card payment-card mt-6">
              <div className="adm-card-header">
                <h3>Recent Revenue</h3>
                <Link to="/admin/payments" className="adm-view-all">View All</Link>
              </div>
              <div className="adm-card-body">
                {data.payments.length > 0 ? (
                  data.payments.map((p, i) => (
                    <div key={i} className="adm-payment-item">
                      <div className="adm-pay-info">
                        <div className="adm-pay-hr">{p.hr?.fullName}</div>
                        <div className="adm-pay-meta">{p.planType || "Plan not set"}</div>
                      </div>
                      <div className="adm-pay-amount">Rs {p.amount}</div>
                    </div>
                  ))
                ) : (
                  <div className="adm-empty-msg">No recent transactions.</div>
                )}
              </div>
            </div>
          </div>

          <div className="adm-column wide">
            <div className="adm-card table-card">
              <div className="adm-card-header">
                <h3>Recent Candidate Activity</h3>
                <div className="adm-card-search">
                  <Search size={16} />
                  <input type="text" placeholder="Quick find..." readOnly />
                </div>
              </div>
              <div className="adm-table-container">
                <table>
                  <thead>
                    <tr>
                      <th>Candidate</th>
                      <th>Assessment</th>
                      <th>Level</th>
                      <th>Badge</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.candidates.slice(0, 8).map((c) => (
                      <tr key={c.id}>
                        <td>
                          <div className="adm-t-name">{c.fullName}</div>
                          <div className="adm-t-email">{c.email}</div>
                        </td>
                        <td>
                          <div className="adm-t-score">{c.score ? `${c.score}% score` : "Pending review"}</div>
                        </td>
                        <td>{c.experienceLevel || "Fresher"}</td>
                        <td>
                          {c.badge ? <span className="adm-t-badge">{c.badge}</span> : <span className="adm-t-none">-</span>}
                        </td>
                        <td>
                          <Link to={`/admin/candidates/${c.id}`} className="adm-t-btn adm-t-btn-primary">View Profile</Link>
                        </td>
                      </tr>
                    ))}
                    {data.candidates.length === 0 ? (
                      <tr>
                        <td colSpan="5" style={{ textAlign: "center", padding: "36px 0", color: "#64748b" }}>
                          No candidate activity available.
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </AdminLayout>
  );
}