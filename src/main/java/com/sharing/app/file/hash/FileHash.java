package com.sharing.app.file.hash;

import com.sharing.app.PojaGenerated;

@PojaGenerated
public record FileHash(FileHashAlgorithm algorithm, String value) {}
