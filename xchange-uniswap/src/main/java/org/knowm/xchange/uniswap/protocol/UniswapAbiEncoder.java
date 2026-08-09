package org.knowm.xchange.uniswap.protocol;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * ABI encoders for the Uniswap v4 contracts the module talks to, pinned to:
 *
 * <ul>
 *   <li>Universal Router 2.1.1 ({@code Commands.V4_SWAP = 0x10}, {@code PERMIT2_PERMIT = 0x0a})
 *   <li>v4-periphery V4Router actions ({@code SWAP_EXACT_IN_SINGLE = 0x06}, {@code
 *       SWAP_EXACT_OUT_SINGLE = 0x08}, {@code SETTLE = 0x0b}, {@code TAKE = 0x0e})
 *   <li>v4-periphery V4Quoter lens ({@code quoteExactInputSingle} / {@code quoteExactOutputSingle})
 *   <li>Permit2 allowance transfer ({@code approve}, {@code allowance})
 * </ul>
 *
 * <p>Command and action constants are pinned from the deployed contract sources:
 *
 * <pre>
 * universal-router @ 2.1.1 (999d561c)  contracts/libraries/Commands.sol
 * v4-periphery     @ 3231810e          src/libraries/Actions.sol, src/interfaces/IV4Router.sol
 * v4-periphery     @ 3231810e          src/interfaces/IV4Quoter.sol
 * permit2          @ cc56ad0f          src/interfaces/IAllowanceTransfer.sol
 * </pre>
 */
public final class UniswapAbiEncoder {

  /** Universal Router command id for a v4 action bundle. */
  public static final byte COMMAND_V4_SWAP = 0x10;

  private static final byte[] SELECTOR_EXECUTE = Abi.selector("execute(bytes,bytes[],uint256)");

  /** v4 router actions. */
  public static final byte ACTION_SWAP_EXACT_IN_SINGLE = 0x06;
  public static final byte ACTION_SWAP_EXACT_OUT_SINGLE = 0x08;
  public static final byte ACTION_SETTLE = 0x0b;
  public static final byte ACTION_TAKE = 0x0e;

  /** ActionConstants.OPEN_DELTA and ActionConstants.MSG_SENDER from v4-periphery. */
  public static final BigInteger OPEN_DELTA = BigInteger.ZERO;
  public static final String MSG_SENDER = "0x0000000000000000000000000000000000000001";

  /** WETH9 mainnet address used by Uniswap deployments. */
  public static final String MAINNET_WETH = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";

  private UniswapAbiEncoder() {}

  // ---------------------------------------------------------------------------------------------
  // Pool key
  // ---------------------------------------------------------------------------------------------

  /**
   * ABI-encodes a v4 {@code PoolKey} struct: {@code (address currency0, address currency1, uint24
   * fee, int24 tickSpacing, address hooks)} — five words.
   */
  public static byte[] encodePoolKey(
      String currency0, String currency1, int fee, int tickSpacing, String hooks) {
    return Abi.concat(
        Abi.address(currency0),
        Abi.address(currency1),
        Abi.word(fee),
        Abi.word(tickSpacing),
        Abi.address(hooks));
  }

  /** The v4 pool id: {@code keccak256(abi.encode(PoolKey))}. */
  public static String poolId(byte[] encodedPoolKey) {
    return Abi.toHex(org.web3j.crypto.Hash.sha3(encodedPoolKey));
  }

  // ---------------------------------------------------------------------------------------------
  // Quoter
  // ---------------------------------------------------------------------------------------------

  private static final byte[] SELECTOR_QUOTE_EXACT_INPUT_SINGLE =
      Abi.selector("quoteExactInputSingle((address,address,uint24,int24,address),bool,uint128,bytes)");
  private static final byte[] SELECTOR_QUOTE_EXACT_OUTPUT_SINGLE =
      Abi.selector(
          "quoteExactOutputSingle((address,address,uint24,int24,address),bool,uint128,bytes)");

  /**
   * Calldata for {@code V4Quoter.quoteExactInputSingle(QuoteExactSingleParams)} where {@code
   * QuoteExactSingleParams} is {@code (PoolKey, bool, uint128, bytes)}.
   */
  public static byte[] quoteExactInputSingleCalldata(
      byte[] encodedPoolKey, boolean zeroForOne, BigInteger exactAmount, byte[] hookData) {
    return quoteSingleCalldata(SELECTOR_QUOTE_EXACT_INPUT_SINGLE, encodedPoolKey, zeroForOne, exactAmount, hookData);
  }

  /**
   * Calldata for {@code V4Quoter.quoteExactOutputSingle(QuoteExactSingleParams)} where {@code
   * QuoteExactSingleParams} is {@code (PoolKey, bool, uint128, bytes)}.
   */
  public static byte[] quoteExactOutputSingleCalldata(
      byte[] encodedPoolKey, boolean zeroForOne, BigInteger exactAmount, byte[] hookData) {
    return quoteSingleCalldata(
        SELECTOR_QUOTE_EXACT_OUTPUT_SINGLE, encodedPoolKey, zeroForOne, exactAmount, hookData);
  }

  private static byte[] quoteSingleCalldata(
      byte[] selector, byte[] encodedPoolKey, boolean zeroForOne, BigInteger exactAmount, byte[] hookData) {
    // head: poolKey (5 words) + zeroForOne + exactAmount + hookData offset = 8 words
    byte[] head = Abi.concat(encodedPoolKey, Abi.bool(zeroForOne), Abi.word(exactAmount), Abi.word(8L * Abi.WORD));
    byte[] tail = Abi.dynamicBytes(hookData);
    return Abi.concat(selector, head, tail);
  }

  // ---------------------------------------------------------------------------------------------
  // Universal Router v4 swap actions
  // ---------------------------------------------------------------------------------------------

  /**
   * ABI-encodes the {@code V4SwapParams} struct for {@code SWAP_EXACT_IN_SINGLE}: {@code (PoolKey,
   * bool, uint128, uint128, uint256, bytes)} — {@code amountIn}, {@code amountOutMinimum}, {@code
   * minHopPriceX36} ({@code 0} disables the per-hop price check), {@code hookData}.
   */
  public static byte[] encodeExactInputSingleParams(
      byte[] encodedPoolKey,
      boolean zeroForOne,
      BigInteger amountIn,
      BigInteger amountOutMinimum,
      BigInteger minHopPriceX36,
      byte[] hookData) {
    // head: poolKey (5) + zeroForOne + amountIn + amountOutMinimum + minHopPriceX36 + hookData offset = 10 words
    byte[] head =
        Abi.concat(
            encodedPoolKey,
            Abi.bool(zeroForOne),
            Abi.word(amountIn),
            Abi.word(amountOutMinimum),
            Abi.word(minHopPriceX36),
            Abi.word(10L * Abi.WORD));
    return Abi.concat(head, Abi.dynamicBytes(hookData));
  }

  /**
   * ABI-encodes the {@code V4SwapParams} struct for {@code SWAP_EXACT_OUT_SINGLE}: {@code
   * (PoolKey, bool, uint128, uint128, uint256, bytes)} — {@code amountOut}, {@code
   * amountInMaximum}, {@code minHopPriceX36}, {@code hookData}.
   */
  public static byte[] encodeExactOutputSingleParams(
      byte[] encodedPoolKey,
      boolean zeroForOne,
      BigInteger amountOut,
      BigInteger amountInMaximum,
      BigInteger minHopPriceX36,
      byte[] hookData) {
    return encodeExactInputSingleParams(
        encodedPoolKey, zeroForOne, amountOut, amountInMaximum, minHopPriceX36, hookData);
  }

  /**
   * ABI-encodes the {@code SETTLE} action params: {@code (Currency, uint256, bool)}. Using {@link
   * #OPEN_DELTA} settles the full open debt of the router; {@code payerIsUser} pays from the caller
   * through Permit2.
   */
  public static byte[] encodeSettleParams(String currencyAddress, BigInteger amount, boolean payerIsUser) {
    return Abi.concat(Abi.address(currencyAddress), Abi.word(amount), Abi.bool(payerIsUser));
  }

  /**
   * ABI-encodes the {@code TAKE} action params: {@code (Currency, address, uint256)}. Using {@link
   * #OPEN_DELTA} takes the full open credit; {@link #MSG_SENDER} sends it to the caller.
   */
  public static byte[] encodeTakeParams(String currencyAddress, String recipient, BigInteger amount) {
    return Abi.concat(Abi.address(currencyAddress), Abi.address(recipient), Abi.word(amount));
  }

  /**
   * ABI-encodes the input of the {@code V4_SWAP} Universal Router command: {@code abi.encode(bytes
   * actions, bytes[] params)}.
   */
  public static byte[] encodeV4Actions(byte[] actionIds, List<byte[]> actionParams) {
    if (actionIds.length != actionParams.size()) {
      throw new IllegalArgumentException("action ids and params length mismatch");
    }
    byte[] actions = new byte[actionIds.length];
    System.arraycopy(actionIds, 0, actions, 0, actionIds.length);
    byte[] actionsBlob = Abi.dynamicBytes(actions);
    byte[] paramsBlob = Abi.dynamicBytesArray(actionParams);
    // abi.encode(bytes, bytes[]): offset to actions, offset to params, actions, params
    int actionsOffset = 2 * Abi.WORD;
    int paramsOffset = actionsOffset + actionsBlob.length;
    return Abi.concat(Abi.word(actionsOffset), Abi.word(paramsOffset), actionsBlob, paramsBlob);
  }

  /**
   * Calldata for {@code UniversalRouter.execute(bytes commands, bytes[] inputs, uint256 deadline)}:
   * selector followed by the ABI-encoded arguments.
   */
  public static byte[] encodeExecuteCalldata(byte[] commandIds, List<byte[]> commandInputs, BigInteger deadline) {
    if (commandIds.length != commandInputs.size()) {
      throw new IllegalArgumentException("command ids and inputs length mismatch");
    }
    byte[] commands = new byte[commandIds.length];
    System.arraycopy(commandIds, 0, commands, 0, commandIds.length);
    byte[] commandsBlob = Abi.dynamicBytes(commands);
    byte[] inputsBlob = Abi.dynamicBytesArray(commandInputs);
    // head: commands offset, inputs offset, deadline
    int commandsOffset = 3 * Abi.WORD;
    int inputsOffset = commandsOffset + commandsBlob.length;
    return Abi.concat(
        SELECTOR_EXECUTE,
        Abi.word(commandsOffset),
        Abi.word(inputsOffset),
        Abi.word(deadline),
        commandsBlob,
        inputsBlob);
  }

  /**
   * The full v4 single-pool swap action bundle for the Universal Router: settle the input from the
   * caller, swap, take the output to the caller.
   */
  public static List<byte[]> singlePoolSwapActions(
      boolean exactInput,
      byte[] encodedPoolKey,
      boolean zeroForOne,
      BigInteger amountSpecified,
      BigInteger limitAmount,
      String inputCurrencyAddress,
      String outputCurrencyAddress,
      byte[] hookData) {
    byte[] swapParams =
        exactInput
            ? encodeExactInputSingleParams(
                encodedPoolKey, zeroForOne, amountSpecified, limitAmount, BigInteger.ZERO, hookData)
            : encodeExactOutputSingleParams(
                encodedPoolKey, zeroForOne, amountSpecified, limitAmount, BigInteger.ZERO, hookData);
    byte[] settleParams = encodeSettleParams(inputCurrencyAddress, OPEN_DELTA, true);
    byte[] takeParams = encodeTakeParams(outputCurrencyAddress, MSG_SENDER, OPEN_DELTA);
    List<byte[]> params = new ArrayList<>(3);
    params.add(swapParams);
    params.add(settleParams);
    params.add(takeParams);
    return params;
  }

  // ---------------------------------------------------------------------------------------------
  // Permit2
  // ---------------------------------------------------------------------------------------------

  private static final byte[] SELECTOR_PERMIT2_APPROVE =
      Abi.selector("approve(address,address,uint160,uint48)");
  private static final byte[] SELECTOR_PERMIT2_ALLOWANCE =
      Abi.selector("allowance(address,address,address)");

  /** Calldata for {@code Permit2.approve(address token, address spender, uint160 amount, uint48 expiration)}. */
  public static byte[] permit2ApproveCalldata(
      String tokenAddress, String spenderAddress, BigInteger amount, BigInteger expiration) {
    return Abi.concat(
        SELECTOR_PERMIT2_APPROVE,
        Abi.address(tokenAddress),
        Abi.address(spenderAddress),
        Abi.word(amount),
        Abi.word(expiration));
  }

  /** Calldata for {@code Permit2.allowance(address user, address token, address spender)}. */
  public static byte[] permit2AllowanceCalldata(
      String userAddress, String tokenAddress, String spenderAddress) {
    return Abi.concat(
        SELECTOR_PERMIT2_ALLOWANCE,
        Abi.address(userAddress),
        Abi.address(tokenAddress),
        Abi.address(spenderAddress));
  }

  // ---------------------------------------------------------------------------------------------
  // ERC-20
  // ---------------------------------------------------------------------------------------------

  private static final byte[] SELECTOR_BALANCE_OF = Abi.selector("balanceOf(address)");
  private static final byte[] SELECTOR_DECIMALS = Abi.selector("decimals()");

  /** Calldata for {@code ERC20.balanceOf(address)}. */
  public static byte[] balanceOfCalldata(String ownerAddress) {
    return Abi.concat(SELECTOR_BALANCE_OF, Abi.address(ownerAddress));
  }

  /** Calldata for {@code ERC20.decimals()}. */
  public static byte[] decimalsCalldata() {
    return SELECTOR_DECIMALS.clone();
  }
}
