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
      int skip = cursor == null ? 0 : cursor.getSkip();
      List<String> providerItems = new ArrayList<>();
      if (page <= totalPages) {
        for (int i = 0; i < pageSize; i++) {
          providerItems.add("p" + page + "-" + i);
        }
      }
      // resume state: drop the prefix already consumed by a previous bounded run
      List<String> items =
          skip == 0
              ? providerItems
              : providerItems.size() <= skip
                  ? List.of()
                  : new ArrayList<>(providerItems.subList(skip, providerItems.size()));
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
    // the ceiling is a hard bound: the page that crosses it is cut, never over-returned
    assertThat(result.getItems()).hasSize(7);
    assertThat(result.getItems().get(6)).isEqualTo("p3-0");
    // resumable: the cut page with the in-page skip advanced, so the tail is not lost
    assertThat(result.getNextCursor()).isEqualTo(GateioPageCursor.page(3).withSkip(1));

    // resume drops the consumed prefix of page 3, then continues page by page
    GateioPage<String> resumedPage = planFetcher(3, 5).fetch(result.getNextCursor());
    assertThat(resumedPage.getItems()).containsExactly("p3-1", "p3-2");
    assertThat(resumedPage.getNextCursor()).isEqualTo(GateioPageCursor.page(4));

    GateioContinuation<String> resumed =
        GateioPagination.iterate(planFetcher(3, 5), 100);
    assertThat(resumed.getItems()).hasSize(15);
  }

  @Test
  void iterate_ceilingOnFirstPage_stopsImmediately() throws IOException {
    GateioContinuation<String> result = GateioPagination.iterate(planFetcher(10, 2), 5);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(result.getItems()).hasSize(5);
    assertThat(result.getNextCursor()).isEqualTo(GateioPageCursor.page(1).withSkip(5));

    // the cut tail of the first page remains reachable
    GateioPage<String> resumedPage = planFetcher(10, 2).fetch(result.getNextCursor());
    assertThat(resumedPage.getItems()).hasSize(5);
    assertThat(resumedPage.getItems().get(0)).isEqualTo("p1-5");
    assertThat(resumedPage.getNextCursor()).isEqualTo(GateioPageCursor.page(2));
  }

  @Test
  void iterate_exhaustedPageStillHonorsCeiling() throws IOException {
    GateioContinuation<String> result = GateioPagination.iterate(planFetcher(10, 1), 5);

    assertThat(result.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(result.getItems()).hasSize(5);
    // the provider is exhausted, but the cut tail of the last page is still resumable
    assertThat(result.getNextCursor()).isEqualTo(GateioPageCursor.page(1).withSkip(5));

    GateioPage<String> resumedPage = planFetcher(10, 1).fetch(result.getNextCursor());
    assertThat(resumedPage.getItems()).containsExactly("p1-5", "p1-6", "p1-7", "p1-8", "p1-9");
    assertThat(resumedPage.getNextCursor()).isNull();
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
    assertThat(result.getNextCursor()).isNull();
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
    assertThat(result.getNextCursor()).isNull();
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
    // the in-page skip participates in value semantics
    assertThat(GateioPageCursor.page(3).withSkip(1))
        .isEqualTo(GateioPageCursor.page(3).withSkip(1));
    assertThat(GateioPageCursor.page(3)).isNotEqualTo(GateioPageCursor.page(3).withSkip(1));
    assertThat(GateioPageCursor.page(3).withSkip(5).getSkip()).isEqualTo(5);
  }

  @Test
  void pageCursor_invalidInput_rejected() {
    assertThatThrownBy(() -> GateioPageCursor.page(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GateioPageCursor.offset(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GateioPageCursor.afterId(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GateioPageCursor.since(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GateioPageCursor.page(2).withSkip(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
