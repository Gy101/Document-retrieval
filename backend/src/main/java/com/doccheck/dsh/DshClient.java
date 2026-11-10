package com.doccheck.dsh;

/**
 * DSH 调用抽象。真实模式使用 HttpDshClient，演示/测试模式使用 MockDshClient。
 */
public interface DshClient {

    DshCompareResult compare(DshCompareRequest request);

    String mode();
}
