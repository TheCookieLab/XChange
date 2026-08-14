package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.okx.dto.trade.OkxPageParams;

/** Offline unit tests for the cursor-paginated iteration used by history endpoints. */
public class OkxPageIteratorTest {

  /** Minimal stand-in for a paginated record exposing an id. */
  record Item(String id) {}

  /**
   * Builds a page fetcher that serves {@code pages} in order and records every {@code after} cursor
   * it is invoked with. Fetching past the supplied pages fails the test (an unexpected extra page).
   */
  private static OkxPageIterator.ThrowingPageFetcher<Item> recordingFetcher(
      List<String> cursors, List<List<Item>> pages) {
    return page -> {
      cursors.add(page.getAfter());
      int index = cursors.size() - 1;
      assertThat(index).as("page fetches exceeded supplied pages").isLessThan(pages.size());
      return pages.get(index);
    };
  }

  private static Item item(String id) {
    return new Item(id);
  }

  @Test
  public void testBeforeBoundIsPreservedWhileAdvancingAfter() throws Exception {
    List<OkxPageParams> seen = new ArrayList<>();
    List<Item> page1 = List.of(item("a"), item("b"));
    List<Item> page2 = List.of(item("c"));
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher =
        page -> {
          seen.add(page);
          return seen.size() == 1 ? page1 : page2;
        };

    List<Item> result =
        OkxPageIterator.fetchAll(fetcher, Item::id, new OkxPageParams(null, "B", 2));

    assertThat(result).extracting(Item::id).containsExactly("a", "b", "c");
    assertThat(seen).extracting(OkxPageParams::getAfter).containsExactly(null, "b");
    // The caller's before bound must survive cursor advancement; clearing it lets subsequent pages
    // run past the requested range into older records.
    assertThat(seen).extracting(OkxPageParams::getBefore).containsExactly("B", "B");
  }

  @Test
  public void testFullPagesThenPartialPageAccumulatesAndStops() throws Exception {
    List<String> cursors = new ArrayList<>();
    List<Item> page1 = List.of(item("a"), item("b"), item("c"));
    List<Item> page2 = List.of(item("d"), item("e"), item("f"));
    List<Item> page3 = List.of(item("g"));
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher =
        recordingFetcher(cursors, Arrays.asList(page1, page2, page3));

    List<Item> result = OkxPageIterator.fetchAll(fetcher, Item::id, OkxPageParams.of(3));

    assertThat(result).extracting(Item::id).containsExactly("a", "b", "c", "d", "e", "f", "g");
    // Cursor advanced after each full page; the partial page was fetched with the last cursor.
    assertThat(cursors).containsExactly(null, "c", "f");
  }

  @Test
  public void testFullPageThenEmptyPageTerminates() throws Exception {
    List<String> cursors = new ArrayList<>();
    List<Item> page1 = List.of(item("a"), item("b"), item("c"));
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher =
        recordingFetcher(cursors, Arrays.asList(page1, Collections.emptyList()));

    List<Item> result = OkxPageIterator.fetchAll(fetcher, Item::id, OkxPageParams.of(3));

    assertThat(result).extracting(Item::id).containsExactly("a", "b", "c");
    assertThat(cursors).containsExactly(null, "c");
  }

  @Test
  public void testRepeatedLastRecordOnFullPageStopsWithoutReAdding() throws Exception {
    List<String> cursors = new ArrayList<>();
    List<Item> page1 = List.of(item("a"), item("b"), item("c"));
    // A "full" page that repeats the previous page's last id means no forward progress.
    List<Item> page2 = List.of(item("d"), item("e"), item("c"));
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher =
        recordingFetcher(cursors, Arrays.asList(page1, page2));

    List<Item> result = OkxPageIterator.fetchAll(fetcher, Item::id, OkxPageParams.of(3));

    assertThat(result).extracting(Item::id).containsExactly("a", "b", "c");
    assertThat(cursors).containsExactly(null, "c");
  }

  @Test
  public void testNullLastIdStopsAfterConsumingPage() throws Exception {
    List<String> cursors = new ArrayList<>();
    List<Item> page1 = List.of(item("a"), new Item(null));
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher =
        recordingFetcher(cursors, Collections.singletonList(page1));

    List<Item> result = OkxPageIterator.fetchAll(fetcher, Item::id, OkxPageParams.of(3));

    assertThat(result).extracting(Item::id).containsExactly("a", (String) null);
    assertThat(cursors).containsExactly((String) null);
  }

  @Test
  public void testEmptyAndNullFirstPageReturnNoItems() throws Exception {
    OkxPageIterator.ThrowingPageFetcher<Item> empty = page -> Collections.emptyList();
    assertThat(OkxPageIterator.fetchAll(empty, Item::id, OkxPageParams.of(3))).isEmpty();

    OkxPageIterator.ThrowingPageFetcher<Item> nullPage = page -> null;
    assertThat(OkxPageIterator.fetchAll(nullPage, Item::id, OkxPageParams.of(3))).isEmpty();
  }

  @Test
  public void testMaxPagesBoundIsEnforced() throws Exception {
    List<String> cursors = new ArrayList<>();
    List<Item> page1 = List.of(item("a"), item("b"), item("c"));
    List<Item> page2 = List.of(item("d"), item("e"), item("f"));
    List<Item> page3 = List.of(item("g"), item("h"), item("i"));
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher =
        recordingFetcher(cursors, Arrays.asList(page1, page2, page3));

    List<Item> result = OkxPageIterator.fetchAll(fetcher, Item::id, OkxPageParams.of(3), 2);

    assertThat(result).extracting(Item::id).containsExactly("a", "b", "c", "d", "e", "f");
    assertThat(cursors).containsExactly(null, "c");
  }

  @Test
  public void testRejectsInvalidMaxPages() {
    OkxPageIterator.ThrowingPageFetcher<Item> fetcher = page -> Collections.emptyList();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> OkxPageIterator.fetchAll(fetcher, Item::id, OkxPageParams.of(3), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxPages");
  }
}
