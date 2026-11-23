package com.collectionhub.domain.port.out;
import com.collectionhub.domain.model.LoanCollectionSnapshot;
public interface CollectionSchedulePort { LoanCollectionSnapshot fetch(long loanId); }
