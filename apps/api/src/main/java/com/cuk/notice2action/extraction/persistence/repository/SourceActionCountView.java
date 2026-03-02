package com.cuk.notice2action.extraction.persistence.repository;

import java.util.UUID;

public interface SourceActionCountView {

  UUID getSourceId();

  long getActionCount();
}
