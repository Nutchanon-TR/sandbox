package com.sandbox.sandman.backend.adapters;

import java.util.List;

public interface JobSourceAdapter {
    String sourceKey();

    JobFetchMode fetchMode();

    List<ExternalJobRef> search(JobSearchCriteria criteria);

    RawJobPayload fetchDetail(ExternalJobRef ref);

    NormalizedJobRecord normalize(RawJobPayload raw);
}
