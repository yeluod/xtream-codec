/*
 * Copyright 2024-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.hylexus.xtream.codec.ext.jt808.builtin.messages.ext.location;

import io.github.hylexus.xtream.codec.common.utils.BitFlag;
import io.github.hylexus.xtream.codec.core.FieldTransformer;
import io.github.hylexus.xtream.codec.common.utils.EnumSetBitTransformer;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * {@code @DerivedField} 在 {@link io.github.hylexus.xtream.codec.ext.jt808.builtin.messages.request.BuiltinMessage0200}
 * 中使用所需的枚举和 Transformer。
 *
 * @author hylexus
 * @author opencode (AI)
 * @since 0.6.0
 */
public final class BuiltinMessage0200Support {

    private BuiltinMessage0200Support() {
    }

    // ========== 报警标志位枚举 ==========
    public enum AlarmFlag implements BitFlag {
        EMERGENCY(0, "紧急报警"),
        OVERSPEED(1, "超速报警"),
        FATIGUE_DRIVING(2, "疲劳驾驶报警"),
        DANGEROUS_DRIVING(3, "危险驾驶行为报警"),
        GNSS_FAULT(4, "GNSS 模块故障"),
        GNSS_ANTENNA_CUT(5, "GNSS 天线未接或被剪断"),
        GNSS_ANTENNA_SHORT(6, "GNSS 天线短路"),
        MAIN_POWER_UNDER_VOLTAGE(7, "终端主电源欠压"),
        MAIN_POWER_FAILURE(8, "终端主电源掉电"),
        LCD_FAULT(9, "终端 LCD 或显示器故障"),
        TTS_FAULT(10, "TTS 模块故障"),
        CAMERA_FAULT(11, "摄像头故障"),
        IC_CARD_FAULT(12, "道路运输证 IC 卡模块故障"),
        OVERSPEED_WARNING(13, "超速预警"),
        FATIGUE_WARNING(14, "疲劳驾驶预警"),
        ILLEGAL_DRIVING(15, "违规行驶报警"),
        TIRE_PRESSURE_WARNING(16, "胎压预警"),
        RIGHT_BLIND_SPOT(17, "右转盲区异常报警"),
        DAILY_DRIVING_OVERTIME(18, "当天累计驾驶超时"),
        PARKING_OVERTIME(19, "超时停车报警"),
        IN_OUT_AREA(20, "进出区域报警"),
        IN_OUT_ROUTE(21, "进出路线报警"),
        SECTION_TIME_ABNORMAL(22, "路段行驶时间不足/过长"),
        ROUTE_DEVIATION(23, "路线偏离报警"),
        VSS_FAULT(24, "车辆 VSS 故障"),
        FUEL_ABNORMAL(25, "车辆油量异常"),
        STOLEN(26, "车辆被盗"),
        ILLEGAL_IGNITION(27, "车辆非法点火"),
        ILLEGAL_DISPLACEMENT(28, "车辆非法位移"),
        COLLISION_ROLLOVER(29, "碰撞侧翻"),
        ROLLOVER_WARNING(30, "侧翻预警");

        private final int offset;
        private final int length;
        private final int bitValue;
        private final String description;

        AlarmFlag(int offset, String description) {
            this(offset, 1, 1, description);
        }

        AlarmFlag(int offset, int length, int bitValue, String description) {
            this.offset = offset;
            this.length = length;
            this.bitValue = bitValue;
            this.description = description;
        }

        @Override
        public int bitOffset() {
            return offset;
        }

        @Override
        public int bitLength() {
            return length;
        }

        @Override
        public int bitValue() {
            return bitValue;
        }

        public String description() {
            return description;
        }
    }

    // ========== 状态位独立标志枚举 ==========
    public enum StatusBit implements BitFlag {
        ACC_ON(0, "ACC 开"),
        GPS_LOCKED(1, "已定位"),
        SOUTH_LATITUDE(2, "南纬"),
        WEST_LONGITUDE(3, "西经"),
        STOPPED(4, "停运状态"),
        ENCRYPTED(5, "经纬度已加密"),
        FCW(6, "前撞预警"),
        LDW(7, "车道偏移预警"),
        // bits 8~9: 载货状态（多 bit range 示例）
        CARGO_EMPTY(8, 2, 0b00, "空车"),
        CARGO_HALF_LOADED(8, 2, 0b01, "半载"),
        CARGO_RESERVED(8, 2, 0b10, "保留"),
        CARGO_FULL_LOADED(8, 2, 0b11, "满载"),
        FUEL_CUT(10, "油路断开"),
        POWER_CUT(11, "电路断开"),
        DOOR_LOCKED(12, "车门加锁"),
        DOOR1_OPEN(13, "门 1 开"),
        DOOR2_OPEN(14, "门 2 开"),
        DOOR3_OPEN(15, "门 3 开"),
        DOOR4_OPEN(16, "门 4 开"),
        DOOR5_OPEN(17, "门 5 开"),
        GPS_USED(18, "使用 GPS 定位"),
        BEIDOU_USED(19, "使用北斗定位"),
        GLONASS_USED(20, "使用 GLONASS 定位"),
        GALILEO_USED(21, "使用 Galileo 定位"),
        MOVING(22, "车辆行驶中"),
        ;

        private final int offset;
        private final int length;
        private final int bitValue;
        private final String description;

        StatusBit(int offset, String description) {
            this(offset, 1, 1, description);
        }

        StatusBit(int offset, int length, int bitValue, String description) {
            this.offset = offset;
            this.length = length;
            this.bitValue = bitValue;
            this.description = description;
        }

        @Override
        public int bitOffset() {
            return offset;
        }

        @Override
        public int bitLength() {
            return length;
        }

        @Override
        public int bitValue() {
            return bitValue;
        }

        public String description() {
            return description;
        }
    }

    // ========== Transformer 实现 ==========

    /// 报警标志位 → EnumSet<AlarmFlag>（3 行桥接子类）
    public static class AlarmFlagTransformer extends EnumSetBitTransformer<AlarmFlag> {
        public AlarmFlagTransformer() {
            super(AlarmFlag.class);
        }
    }

    /// 报警标志 → 人可读描述（自定义 S→T，只读，无 write-back）
    public static class AlarmDescriptionTransformer implements FieldTransformer<Long, String> {
        @Override
        public @Nullable String read(@Nullable Long source) {
            if (source == null) {
                return null;
            }
            return EnumSet.allOf(AlarmFlag.class).stream()
                    .filter(flag -> {
                        // 之前的实现: (source & (1L << flag.bitOffset())) != 0
                        // 这个写法只对单 bit 标志有效，多 bit range（如 bitLength > 1）
                        // 无法正确处理：bitValue=0 永不匹配，非 0 也只检查了 range 的第一位
                        if (flag.bitLength() == 1) {
                            return (source & (1L << flag.bitOffset())) != 0;
                        }
                        final long rangeVal = (source >>> flag.bitOffset()) & ((1L << flag.bitLength()) - 1);
                        return rangeVal == flag.bitValue();
                    })
                    .map(AlarmFlag::description)
                    .collect(Collectors.joining(" | "));
        }
    }

    /// 状态位 → EnumSet<StatusBit>（含独立 bit 标志 + 多 bit range 载货状态）
    public static class StatusBitTransformer extends EnumSetBitTransformer<StatusBit> {
        public StatusBitTransformer() {
            super(StatusBit.class);
        }
    }
}
