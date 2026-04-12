package com.example.attendify.repository;

import com.example.attendify.models.MockApprovalData;
import com.example.attendify.models.ApprovalRequest;

import java.util.List;

/**
 * Access point for excuse / approval requests.
 *
 * Used by: HomeFragment (teacher — pending count + card),
 *          ApprovalRequestsFragment (teacher),
 *          and eventually secretary views.
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace method body with ApiService.getPendingApprovals() or
 *   ApprovalDao.getPending().
 */
public class ApprovalRepository {

    private static ApprovalRepository instance;

    private ApprovalRepository() {}

    public static ApprovalRepository getInstance() {
        if (instance == null) instance = new ApprovalRepository();
        return instance;
    }

    public List<ApprovalRequest> getPendingApprovals() {
        return MockApprovalData.getPendingApprovals(); // ← swap: ApiService.getPendingApprovals()
    }
}