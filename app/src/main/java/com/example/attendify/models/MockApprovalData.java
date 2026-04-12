package com.example.attendify.models;

import com.example.attendify.models.ApprovalRequest;

import java.util.Arrays;
import java.util.List;

/**
 * Mock pending approval/excuse requests (used by teacher's HomeFragment
 * and ApprovalRequestsFragment).
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace getPendingApprovals() with an API call — e.g.
 *   ApiService.getPendingApprovals() or ApprovalDao.getPending().
 */
public class MockApprovalData {

    public static List<ApprovalRequest> getPendingApprovals() {
        return Arrays.asList(
                new ApprovalRequest(1, "Desaliza, Cyrus", "D",
                        "April 9–10, 2026", "Fever and flu. Attached medical certificate."),
                new ApprovalRequest(2, "Lozano, Nash",    "L",
                        "April 11, 2026",   "Family emergency out of town."),
                new ApprovalRequest(3, "Puti, Jericho",   "P",
                        "April 8, 2026",    "Dental appointment. Has clinic certificate.")
        );
    }
}