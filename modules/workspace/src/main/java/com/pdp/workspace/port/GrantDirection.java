package com.pdp.workspace.port;

/**
 * 跨工作空间协作授权查询方向。
 *
 * <ul>
 *   <li>{@link #OUTGOING}：当前工作空间作为授权方授予他空间；</li>
 *   <li>{@link #INCOMING}：当前工作空间作为被授权方接收他空间授权。</li>
 * </ul>
 */
public enum GrantDirection {
    OUTGOING,
    INCOMING
}
