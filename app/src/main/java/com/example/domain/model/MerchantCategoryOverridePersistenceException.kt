package com.example.domain.model

/** The financial transaction was committed, but the optional merchant preference could not be persisted. */
class MerchantCategoryOverridePersistenceException(cause: Throwable) :
  Exception("Transaction was saved, but the merchant category preference could not be saved", cause)
