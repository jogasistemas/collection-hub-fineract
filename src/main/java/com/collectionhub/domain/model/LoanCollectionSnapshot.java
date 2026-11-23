package com.collectionhub.domain.model;
import java.util.List;
public record LoanCollectionSnapshot(long loanId, Long clientId, String clientName, String currency,
                                     List<CollectionOrder> orders) {}
