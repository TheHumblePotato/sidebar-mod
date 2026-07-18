package com.lmssmp.sidebar;

/**
 * One entry in the sidebar's capture point section.
 *
 * Only a name is implemented for Milestone 8 -- owner and progress will
 * be added as separate fields once a later milestone actually has
 * ownership/progress data to read (from tagged armor stands). Keeping
 * this minimal now avoids modeling fields nothing produces or consumes
 * yet.
 */
public record CapturePointEntry(String name) {
}
