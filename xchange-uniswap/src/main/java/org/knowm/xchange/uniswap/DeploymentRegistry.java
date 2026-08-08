package org.knowm.xchange.uniswap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.knowm.xchange.uniswap.util.Addresses;

/**
 * Immutable registry of the Uniswap v4 mainnet deployment used for execution and quoting.
 *
 * <p>Addresses default to the official deployment registry (Ethereum mainnet) for Universal Router
 * 2.1.1, the v4 PoolManager, the v4 Quoter lens, Permit2, and WETH9. Expected runtime-code hashes
 * must be pinned by the operator before startup; verification is fail-closed.
 */
public final class DeploymentRegistry {

  /** Contracts whose runtime code is verified against the expected hashes at startup. */
  public enum Contract {
    POOL_MANAGER,
    QUOTER,
    UNIVERSAL_ROUTER,
    PERMIT2
  }

  /**
   * @param poolManager v4 PoolManager address
   * @param quoter v4 Quoter lens address
   * @param universalRouter Universal Router 2.1.1 address
   * @param permit2 Permit2 address
   * @param weth WETH9 address (not code-verified; ERC-20 standard)
   * @param expectedCodeHashes keccak-256 of each contract's runtime code, keyed by {@link Contract}
   */
  public record Deployment(
      String poolManager,
      String quoter,
      String universalRouter,
      String permit2,
      String weth,
      Map<Contract, String> expectedCodeHashes) {

    public Deployment {
      poolManager = Addresses.requireValidAddress(poolManager);
      quoter = Addresses.requireValidAddress(quoter);
      universalRouter = Addresses.requireValidAddress(universalRouter);
      permit2 = Addresses.requireValidAddress(permit2);
      weth = Addresses.requireValidAddress(weth);
      expectedCodeHashes = Collections.unmodifiableMap(new LinkedHashMap<>(expectedCodeHashes));
      for (Contract contract : Contract.values()) {
        String hash = expectedCodeHashes.get(contract);
        if (hash == null || !hash.toLowerCase().startsWith("0x") || hash.length() != 66) {
          throw new IllegalArgumentException(
              "missing or invalid expected code hash for " + contract
                  + "; pin keccak-256 of the runtime code from the official deployment registry");
        }
      }
    }

    /** Normalized (lowercase) address of a contract. */
    public String address(Contract contract) {
      switch (contract) {
        case POOL_MANAGER:
          return poolManager;
        case QUOTER:
          return quoter;
        case UNIVERSAL_ROUTER:
          return universalRouter;
        case PERMIT2:
          return permit2;
        default:
          throw new IllegalArgumentException("unknown contract " + contract);
      }
    }

    /** The expected runtime-code hash of a contract (lowercase). */
    public String expectedCodeHash(Contract contract) {
      return expectedCodeHashes.get(contract).toLowerCase();
    }
  }

  private DeploymentRegistry() {}

  /**
   * Official Ethereum mainnet deployment addresses from the Uniswap deployment registry. Code
   * hashes are intentionally absent: the operator must pin them from their own node or the official
   * registry before the module will start.
   */
  public static Deployment mainnetDefaults() {
    return new Deployment(
        "0x000000000004444c5dc75cB358380D2e3dE08A90", // PoolManager
        "0x52f0e24d1c21c8a0cb1e5a5dd6198556bd9e1203", // V4Quoter lens
        "0x4c82d1fbfe28c977cbb58d8c7ff8fcf9f70a2cca", // Universal Router 2.1.1
        "0x000000000022D473030F116dDEE9F6B43aC78BA3", // Permit2
        "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", // WETH9
        Collections.emptyMap());
  }
}
