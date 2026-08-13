package org.knowm.xchange.gateio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.gateio.dto.GateioContinuation;
import org.knowm.xchange.gateio.dto.GateioIterationStop;
import org.knowm.xchange.gateio.dto.GateioPage;
import org.knowm.xchange.gateio.dto.GateioPageCursor;

class GateioPaginationTest {

  /** Fetcher driven by an explicit page plan: items per page, then null cursor. */
  private static GateioPagination.PageFetcher<String> planFetcher(
      int pageSize, int totalPages) {
    return cursor -> {
      int page = cursor == null ? 1 : cursor.getPage();
      List<String> items = new ArrayList<>();
      if (page <= totalPages) {
        for (int i = 0; i < pageSize; i++) {
          items.add("p" + page + "-" + i);
        }
      }
      GateioPageCursor next = page < totalPages ? GateioPageCursor.page(page + 1) : null;
      return GateioPage.<String>builder().items(items).nextCursor(next).build();
    };
  }

  @Test
  void iterate_multiPage_completesAndAccumulates() throws IOException {
    GateioContinuation<String> result =
        GateioPagination.iterate(planFetcher(3, 3), Integer.MAX_VALUE);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.COMPLETED);
    assertThat(result.getNextCursor()).isNull();
    assertThat(result.getItems()).hasSize(9);
    assertThat(result.getItems().get(0)).isEqualTo("p1-0");
    assertThat(result.getItems().get(8)).isEqualTo("p3-2");
  }

  @Test
  void iterate_singlePage_completes() throws IOException {
    GateioContinuation<String> result =
        GateioPagination.iterate(planFetcher(2, 1), Integer.MAX_VALUE);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.COMPLETED);
    assertThat(result.getItems()).containsExactly("p1-0", "p1-1");
  }

  @Test
  void iterate_ceiling_stopsAtMaxResultsAndResumes() throws IOException {
    GateioContinuation<String> result = GateioPagination.iterate(planFetcher(3, 5), 7);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    // pages are atomic: the page that crosses the ceiling is fully consumed
    assertThat(result.getItems()).hasSize(9);
    // resumable: cursor for the page after the last consumed one
    assertThat(result.getNextCursor()).isNotNull();
    assertThat(result.getNextCursor().getPage()).isEqualTo(4);

    GateioContinuation<String> resumed =
        GateioPagination.iterate(
            cursor -> planFetcher(3, 5).fetch(cursor), 100);
    assertThat(resumed.getItems()).hasSize(15);
  }

  @Test
  void iterate_ceilingOnFirstPage_stopsImmediately() throws IOException {
    GateioContinuation<String> result = GateioPagination.iterate(planFetcher(10, 2), 5);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(result.getItems()).hasSize(10);
    assertThat(result.getNextCursor().getPage()).isEqualTo(2);
  }

  @Test
  void iterate_exhaustedCollectionWinsOverCeiling() throws IOException {
    GateioContinuation<String> result = GateioPagination.iterate(planFetcher(10, 1), 5);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.COMPLETED);
    assertThat(result.getItems()).hasSize(10);
    assertThat(result.getNextCursor()).isNull();
  }

  @Test
  void iterate_repeatedCursor_stopsInsteadOfLooping() throws IOException {
    // fetcher always returns the same next cursor after page 1
    GateioPagination.PageFetcher<String> looping =
        cursor -> {
          if (cursor == null) {
            return GateioPage.<String>builder()
                .items(Arrays.asList("a", "b"))
                .nextCursor(GateioPageCursor.page(2))
                .build();
          }
          return GateioPage.<String>builder()
              .items(Arrays.asList("c", "d"))
              .nextCursor(GateioPageCursor.page(2))
              .build();
        };

    GateioContinuation<String> result = GateioPagination.iterate(looping, Integer.MAX_VALUE);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.REPEATED_CURSOR);
    assertThat(result.getItems()).containsExactly("a", "b", "c", "d");
  }

  @Test
  void iterate_emptyPageWithNextCursor_stopsNoProgress() throws IOException {
    GateioPagination.PageFetcher<String> noProgress =
        cursor -> GateioPage.<String>builder()
            .items(List.of())
            .nextCursor(GateioPageCursor.afterId("still-going"))
            .build();

    GateioContinuation<String> result = GateioPagination.iterate(noProgress, Integer.MAX_VALUE);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.NO_PROGRESS);
    assertThat(result.getItems()).isEmpty();
  }

  @Test
  void iterate_fetcherError_propagates() {
    GateioPagination.PageFetcher<String> failing =
        cursor -> {
          throw new IOException("boom");
        };

    assertThatThrownBy(() -> GateioPagination.iterate(failing, 10))
        .isInstanceOf(IOException.class)
        .hasMessage("boom");
  }

  @Test
  void iterate_zeroCeiling_rejected() {
    assertThatThrownBy(() -> GateioPagination.iterate(planFetcher(1, 1), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxResults must be > 0");
  }

  @Test
  void pageCursor_styles_areDistinct() {
    assertThat(GateioPageCursor.page(2)).isEqualTo(GateioPageCursor.page(2));
    assertThat(GateioPageCursor.page(2)).isNotEqualTo(GateioPageCursor.offset(2));
    assertThat(GateioPageCursor.afterId("x")).isEqualTo(GateioPageCursor.afterId("x"));
    assertThat(GateioPageCursor.afterId("x")).isNotEqualTo(GateioPageCursor.afterId("y"));
    assertThat(GateioPageCursor.since(100)).isEqualTo(GateioPageCursor.since(100));
    assertThat(GateioPageCursor.page(2)).isNotEqualTo(GateioPageCursor.since(2));
  }

  @Test
  void pageCursor_invalidInput_rejected() {
    assertThatThrownBy(() -> GateioPageCursor.page(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GateioPageCursor.offset(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GateioPageCursor.afterId(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
