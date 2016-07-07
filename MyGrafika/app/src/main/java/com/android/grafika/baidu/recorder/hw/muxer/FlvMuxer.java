package com.android.grafika.baidu.recorder.hw.muxer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.android.grafika.baidu.recorder.api.SessionStateListener;
import com.android.grafika.baidu.recorder.hw.rtmp.RtmpSocket;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


public class FlvMuxer {
    private String url;

    private Thread worker;
    private Looper looper;
    private Handler handler;

    private SrsFlv flv;
    private SrsFlvFrameBytes mFlvMetadata;

    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;
    private static final int UNKNOWN_TRACK = 102;
    private static final String TAG = "FlvMuxer";

    private RtmpSocket mRtmpSocket = null;

    private int mTotalSendBytes = 0;
    private long mLastReportedTime = 0;
    private double mUploadBindwidthInKBps = 0;
    private SessionStateListener mStateListener = null;
    private static final int UPLOAD_BINDWIDTH_REPORT_INTERVAL_IN_MS = 2000;
    
    private volatile long mPtsOfLastSentPacketInMs = 0;
    public static final int THRESHOLD_OF_LATENCY_IN_MS_TO_DROP_PACKET = 2000;
    private static final int CTS_OF_FRAME_IN_MS = 5;

    /**
     * constructor.
     * @param path the http flv url to post to.
     * @param format the mux format, @see FlvMuxer.OutputFormat
     */
    public FlvMuxer(String path, int format) {
        mFlvMetadata = null;

        url = path;
        flv = new SrsFlv();
    }

    /**
     * print the size of bytes in bb
     * @param bb the bytes to print.
     * @param size the total size of bytes to print.
     */
    public static void srs_print_bytes(String tag, ByteBuffer bb, int size) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int bytes_in_line = 16;
        int max = bb.remaining();
        for (i = 0; i < size && i < max; i++) {
            sb.append(String.format("0x%s ", Integer.toHexString(bb.get(i) & 0xFF)));
            if (((i + 1) % bytes_in_line) == 0) {
                Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
        }
    }
    public static void srs_print_bytes(String tag, byte[] bb, int size) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int bytes_in_line = 16;
        int max = bb.length;
        for (i = 0; i < size && i < max; i++) {
            sb.append(String.format("0x%s ", Integer.toHexString(bb[i] & 0xFF)));
            if (((i + 1) % bytes_in_line) == 0) {
                Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
        }
    }

    public void setRtmpSocket(RtmpSocket socket) {
        mRtmpSocket = socket;
    }

    /**
     * Adds a track with the specified format.
     * @param format The media format for the track.
     * @return The track index for this newly added track.
     */
    public int addTrack(MediaFormat format) {
        if (format.getString(MediaFormat.KEY_MIME) == "video/avc") {
            flv.setVideoTrack(format);
            return VIDEO_TRACK;
        } else if (format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm") {
            flv.setAudioTrack(format);
            return AUDIO_TRACK;
        }
        return UNKNOWN_TRACK;
    }

    /**
     * start to the remote SRS for remux.
     */
    public void start() throws IOException {
        mUploadBindwidthInKBps = 0;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cycle();
                } catch (InterruptedException ie) {
                } catch (Exception e) {
                    Log.i(TAG, "worker: thread exception.");
                    e.printStackTrace();
                }
            }
        });
        worker.start();
    }

    /**
     * Make sure you call this when you're done to free up any resources
     * instead of relying on the garbage collector to do this for you at
     * some point in the future.
     */
    public void release() {
        stop();
    }

    /**
     * stop the muxer, disconnect HTTP connection from SRS.
     */
    public void stop() {
        mUploadBindwidthInKBps = 0;
        if (worker == null) {
            return;
        }

        if (looper != null) {
            looper.quit();
        }

        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Log.i(TAG, "worker: join thread failed.");
                e.printStackTrace();
                worker.stop();
            }
            worker = null;
        }

        flv.setHandler(null);

        Log.i(TAG, String.format("worker: muxer closed, url=%s", url));
    }

    public void sendMetaData(double width, double height, double fps, double videobitrate, double audiosamplerate, double audiodatarate) {
        mFlvMetadata = flv.makeMetaData(width, height, fps, videobitrate, audiosamplerate, audiodatarate);
    }

    /**
     * send the annexb frame to SRS over HTTP FLV.
     * @param trackIndex The track index for this sample.
     * @param byteBuf The encoded sample.
     * @param bufferInfo The buffer information related to this sample.
     */
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) throws Exception {
        //Log.i(TAG, String.format("dumps the %s stream %dB, pts=%d", (trackIndex == VIDEO_TRACK) ? "Vdieo" : "Audio", bufferInfo.size, bufferInfo.presentationTimeUs / 1000));
        //FlvMuxer.srs_print_bytes(TAG, byteBuf, bufferInfo.size);

        if (bufferInfo.offset > 0) {
            Log.w(TAG, String.format("encoded frame %dB, offset=%d pts=%dms",
                    bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs / 1000
            ));
        }
        
        if (VIDEO_TRACK == trackIndex) {
//            Log.d(TAG, "Sending video tag at "+bufferInfo.presentationTimeUs);
            flv.writeVideoSample(byteBuf, bufferInfo);
        } else {
//            Log.d(TAG, "Sending audio tag at "+bufferInfo.presentationTimeUs);
            flv.writeAudioSample(byteBuf, bufferInfo);
        }
    }
    
    public long getLastSentPacketPtsInMs() {
        return mPtsOfLastSentPacketInMs;
    }
    
    public void clearSendingBuffer() {
        if (handler != null) {
            Log.w(TAG, "Clear buffered packets dur to weak neiwork condition");
            handler.removeMessages(SrsMessageType.FLV);
        }
    }

    private void cycle() throws Exception {
        // create the handler.
        Looper.prepare();
        looper = Looper.myLooper();
        handler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {                
                if (msg.what != SrsMessageType.FLV) {
                    Log.w(TAG, String.format("worker: drop unkown message, what=%d", msg.what));
                    return;
                }

                if (null == mRtmpSocket || !mRtmpSocket.isConnected()) {
                    Log.e(TAG, "The RtmpSockte is not ready...");
                    return;
                }

                SrsFlvFrame frame = (SrsFlvFrame)msg.obj;
                    
                int ret = mRtmpSocket.sendRTMPPacket(frame.tag.frame.array(), frame.tag.size, frame.dts, frame.type);

                if (ret <= 0) {
                    Log.e(TAG, "Sending rtmp chunk failed...");
                    notifyStreamingError(ret);
                    return;
                }
                mPtsOfLastSentPacketInMs = frame.dts + CTS_OF_FRAME_IN_MS;
                mTotalSendBytes += frame.tag.size;
                long currentTime = System.currentTimeMillis();
                if (currentTime - mLastReportedTime >= UPLOAD_BINDWIDTH_REPORT_INTERVAL_IN_MS) {
                    mUploadBindwidthInKBps = (double)mTotalSendBytes / UPLOAD_BINDWIDTH_REPORT_INTERVAL_IN_MS;
                    mLastReportedTime = currentTime;
                    mTotalSendBytes = 0;
                }
            }
        };
        flv.setHandler(handler);
        
        if (mFlvMetadata != null) {
            flv.rtmp_write_packet(SrsCodecFlvTag.Metadata, 0, 0, 0, mFlvMetadata);
            Log.d(TAG, "Metadata info has been sent.");
        }

        Looper.loop();
    }

    private void notifyStreamingError(int err) {
        Log.d(TAG, "Received native error, errno is : " + err);
        int code = SessionStateListener.ERROR_CODE_OF_UNKNOWN_STREAMING_ERROR;
        switch (err) {
            case -32: // Server refused!
                code = SessionStateListener.ERROR_CODE_OF_PACKET_REFUSED_BY_SERVER;
                break;
            case -35: // Network is poor!
                code = SessionStateListener.ERROR_CODE_OF_WEAK_CONNECTION;
                break;
            case -104: // Server error!
                code = SessionStateListener.ERROR_CODE_OF_SERVER_INTERNAL_ERROR;
                break;
            case -110: // Connection timeout!
                code = SessionStateListener.ERROR_CODE_OF_CONNECTION_TIMEOUT;
                break;
            default:
                break;
        }
        if (mStateListener != null) {
            mStateListener.onSessionError(code);
        }
    }

    public double getUploadBindwidthInKBps() {
        return mUploadBindwidthInKBps;
    }

    public void setStateListener(SessionStateListener listener) {
        mStateListener = listener;
    }

    /**
     * the supported output format for muxer.
     */
    public class OutputFormat {
        public final static int MUXER_OUTPUT_RTMP = 0;
    }

    // E.4.3.1 VIDEODATA
    // Frame Type UB [4]
    // Type of video frame. The following values are defined:
    //     1 = key frame (for AVC, a seekable frame)
    //     2 = inter frame (for AVC, a non-seekable frame)
    //     3 = disposable inter frame (H.263 only)
    //     4 = generated key frame (reserved for server use only)
    //     5 = video info/command frame
    class SrsCodecVideoAVCFrame
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        public final static int Reserved1 = 6;

        public final static int KeyFrame                     = 1;
        public final static int InterFrame                 = 2;
        public final static int DisposableInterFrame         = 3;
        public final static int GeneratedKeyFrame            = 4;
        public final static int VideoInfoFrame                = 5;
    }

    // AVCPacketType IF CodecID == 7 UI8
    // The following values are defined:
    //     0 = AVC sequence header
    //     1 = AVC NALU
    //     2 = AVC end of sequence (lower level NALU sequence ender is
    //         not required or supported)
    class SrsCodecVideoAVCType
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                    = 3;

        public final static int SequenceHeader                 = 0;
        public final static int NALU                         = 1;
        public final static int SequenceHeaderEOF             = 2;
    }

    class AMFDataType {
        public final static byte AMFNumber = 0x00;
        public final static byte AMFBoolean = 0x01;
        public final static byte AMFString = 0x02;
        public final static byte AMFObject = 0x03;
        public final static byte AMFMovieClip = 0x04;    /* reserved, not used */
        public final static byte AMFNull = 0x05;
        public final static byte AMFUndefined = 0x06;
        public final static byte AMFReference = 0x07;
        public final static byte AMFEMCAArray = 0x08;
        public final static byte AMFObjectEnd = 0x09;
        public final static byte AMFStrictArray = 0x0a;
        public final static byte AMFDate = 0x0b;
        public final static byte AMFLongString = 0x0c;
        public final static byte AMFUnsupported = 0x0d;
        public final static byte AMFRecordSet = 0x0e;    /* reserved, not used */
        public final static byte AMFXmlDoc = 0x0f;
        public final static byte AMFTypedObject = 0x10;
        public final static byte AMFAvmPlus = 0x11;        /* switch to AMF3 */
        public final static byte AMFInvalid = (byte)0xff;
    }

    /**
     * E.4.1 FLV Tag, page 75
     */
    class SrsCodecFlvTag
    {
        // set to the zero to reserved, for array map.
        public static final int Reserved = 0x00;

        // 8 = audio
        public static final int Audio = 0x08;
        // 9 = video
        public static final int Video = 0x09;
        // 22 = metadata
        public static final int Metadata = 0x16;
    }

    // E.4.3.1 VIDEODATA
    // CodecID UB [4]
    // Codec Identifier. The following values are defined:
    //     2 = Sorenson H.263
    //     3 = Screen video
    //     4 = On2 VP6
    //     5 = On2 VP6 with alpha channel
    //     6 = Screen video version 2
    //     7 = AVC
    class SrsCodecVideo
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved                = 0;
        public final static int Reserved1                = 1;
        public final static int Reserved2                = 9;

        // for user to disable video, for example, use pure audio hls.
        public final static int Disabled                = 8;

        public final static int SorensonH263             = 2;
        public final static int ScreenVideo             = 3;
        public final static int On2VP6                 = 4;
        public final static int On2VP6WithAlphaChannel = 5;
        public final static int ScreenVideoVersion2     = 6;
        public final static int AVC                     = 7;
    }

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    class SrsAacObjectType
    {
        public final static int Reserved = 0;

        // Table 1.1 – Audio Object Type definition
        // @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
        public final static int AacMain = 1;
        public final static int AacLC = 2;
        public final static int AacSSR = 3;

        // AAC HE = LC+SBR
        public final static int AacHE = 5;
        // AAC HEv2 = LC+SBR+PS
        public final static int AacHEV2 = 29;
    }

    /**
     * the aac profile, for ADTS(HLS/TS)
     * @see "https://github.com/simple-rtmp-server/srs/issues/310"
     */
    class SrsAacProfile
    {
        public final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        public final static int Main = 0;
        public final static int LC = 1;
        public final static int SSR = 2;
    }

    /**
     * the FLV/RTMP supported audio sample rate.
     * Sampling rate. The following values are defined:
     * 0 = 5.5 kHz = 5512 Hz
     * 1 = 11 kHz = 11025 Hz
     * 2 = 22 kHz = 22050 Hz
     * 3 = 44 kHz = 44100 Hz
     */
    class SrsCodecAudioSampleRate
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                 = 4;

        public final static int R5512                     = 0;
        public final static int R11025                    = 1;
        public final static int R22050                    = 2;
        public final static int R44100                    = 3;
    }

    /**
     * the type of message to process.
     */
    class SrsMessageType {
        public final static int FLV = 0x100;
    }

    /**
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    class SrsAvcNaluType
    {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    /**
     * utils functions from srs.
     */
    public class SrsUtils {
        private final static String TAG = "FlvMuxer";

        public boolean srs_bytes_equals(byte[] a, byte[]b) {
            if ((a == null || b == null) && (a != null || b != null)) {
                return false;
            }

            if (a.length != b.length) {
                return false;
            }

            for (int i = 0; i < a.length && i < b.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }

            return true;
        }

        public SrsAnnexbSearch srs_avc_startswith_annexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            SrsAnnexbSearch as = new SrsAnnexbSearch();
            as.match = false;

            int pos = bb.position();
            while (pos < bi.size - 3) {
                // not match.
                if (bb.get(pos) != 0x00 || bb.get(pos + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(pos + 2) == 0x01) {
                    as.match = true;
                    as.nb_start_code = pos + 3 - bb.position();
                    break;
                }

                pos++;
            }

            return as;
        }

        public boolean srs_aac_startswith_adts(ByteBuffer bb, MediaCodec.BufferInfo bi)
        {
            int pos = bb.position();
            if (bi.size - pos < 2) {
                return false;
            }

            // matched 12bits 0xFFF,
            // @remark, we must cast the 0xff to char to compare.
            if (bb.get(pos) != (byte)0xff || (byte)(bb.get(pos + 1) & 0xf0) != (byte)0xf0) {
                return false;
            }

            return true;
        }

        public int srs_codec_aac_ts2rtmp(int profile)
        {
            switch (profile) {
                case SrsAacProfile.Main: return SrsAacObjectType.AacMain;
                case SrsAacProfile.LC: return SrsAacObjectType.AacLC;
                case SrsAacProfile.SSR: return SrsAacObjectType.AacSSR;
                default: return SrsAacObjectType.Reserved;
            }
        }

        public int srs_codec_aac_rtmp2ts(int object_type)
        {
            switch (object_type) {
                case SrsAacObjectType.AacMain: return SrsAacProfile.Main;
                case SrsAacObjectType.AacHE:
                case SrsAacObjectType.AacHEV2:
                case SrsAacObjectType.AacLC: return SrsAacProfile.LC;
                case SrsAacObjectType.AacSSR: return SrsAacProfile.SSR;
                default: return SrsAacProfile.Reserved;
            }
        }
    }

    /**
     * the search result for annexb.
     */
    class SrsAnnexbSearch {
        public int nb_start_code = 0;
        public boolean match = false;
    }

    /**
     * the demuxed tag frame.
     */
    class SrsFlvFrameBytes {
        public ByteBuffer frame;
        public int size;
    }

    /**
     * the muxed flv frame.
     */
    class SrsFlvFrame {
        // the tag bytes.
        public SrsFlvFrameBytes tag;
        // the codec type for audio/aac and video/avc for instance.
        public int avc_aac_type;
        // the frame type, keyframe or not.
        public int frame_type;
        // the tag type, audio, video or data.
        public int type;
        // the dts in ms, tbn is 1000.
        public int dts;

        public boolean is_keyframe() {
            return type == SrsCodecFlvTag.Video && frame_type == SrsCodecVideoAVCFrame.KeyFrame;
        }

        public boolean is_video() {
            return type == SrsCodecFlvTag.Video;
        }

        public boolean is_audio() {
            return type == SrsCodecFlvTag.Audio;
        }
    }

    /**
     * the raw h.264 stream, in annexb.
     */
    class SrsRawH264Stream {
        private SrsUtils utils;
        private final static String TAG = "FlvMuxer";

        public SrsRawH264Stream() {
            utils = new SrsUtils();
        }

        public boolean is_sps(SrsFlvFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);

            return nal_unit_type == SrsAvcNaluType.SPS;
        }

        public boolean is_pps(SrsFlvFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);

            return nal_unit_type == SrsAvcNaluType.PPS;
        }

        public SrsFlvFrameBytes mux_ibp_frame(SrsFlvFrameBytes frame) {
            SrsFlvFrameBytes nalu_header = new SrsFlvFrameBytes();
            nalu_header.size = 4;
            nalu_header.frame = ByteBuffer.allocate(nalu_header.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
            int NAL_unit_length = frame.size;

            // mux the avc NALU in "ISO Base Media File Format"
            // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
            // NALUnitLength
            nalu_header.frame.putInt(NAL_unit_length);

            // reset the buffer.
            nalu_header.frame.rewind();

            //Log.i(TAG, String.format("mux ibp frame %dB", frame.size));
            //FlvMuxer.srs_print_bytes(TAG, nalu_header.frame, 16);

            return nalu_header;
        }

        public void mux_sequence_header(byte[] sps, byte[] pps, int dts, int pts, ArrayList<SrsFlvFrameBytes> frames) {
            // 5bytes sps/pps header:
            //      configurationVersion, AVCProfileIndication, profile_compatibility,
            //      AVCLevelIndication, lengthSizeMinusOne
            // 3bytes size of sps:
            //      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
            // Nbytes of sps.
            //      sequenceParameterSetNALUnit
            // 3bytes size of pps:
            //      numOfPictureParameterSets, pictureParameterSetLength
            // Nbytes of pps:
            //      pictureParameterSetNALUnit

            // decode the SPS:
            // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
            if (true) {
                SrsFlvFrameBytes hdr = new SrsFlvFrameBytes();
                hdr.size = 5;
                hdr.frame = ByteBuffer.allocate(hdr.size);

                // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
                //      Baseline profile profile_idc is 66(0x42).
                //      Main profile profile_idc is 77(0x4d).
                //      Extended profile profile_idc is 88(0x58).
                byte profile_idc = sps[1];
                byte compat_idc = sps[2];
                byte level_idc = sps[3];

                // generate the sps/pps header
                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // configurationVersion
                hdr.frame.put((byte)0x01);
                // AVCProfileIndication
                hdr.frame.put(profile_idc);
                // profile_compatibility
                hdr.frame.put(compat_idc);
                // AVCLevelIndication
                hdr.frame.put(level_idc);
                // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
                // so we always set it to 0x03.
                hdr.frame.put((byte)0xFF);

                // reset the buffer.
                hdr.frame.rewind();
                frames.add(hdr);
            }

            // sps
            if (true) {
                SrsFlvFrameBytes sps_hdr = new SrsFlvFrameBytes();
                sps_hdr.size = 3;
                sps_hdr.frame = ByteBuffer.allocate(sps_hdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfSequenceParameterSets, always 1
                sps_hdr.frame.put((byte) 0xE1);
                // sequenceParameterSetLength
                sps_hdr.frame.putShort((short) sps.length);

                sps_hdr.frame.rewind();
                frames.add(sps_hdr);

                // sequenceParameterSetNALUnit
                SrsFlvFrameBytes sps_bb = new SrsFlvFrameBytes();
                sps_bb.size = sps.length;
                sps_bb.frame = ByteBuffer.wrap(sps);
                frames.add(sps_bb);
            }

            // pps
            if (true) {
                SrsFlvFrameBytes pps_hdr = new SrsFlvFrameBytes();
                pps_hdr.size = 3;
                pps_hdr.frame = ByteBuffer.allocate(pps_hdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfPictureParameterSets, always 1
                pps_hdr.frame.put((byte) 0x01);
                // pictureParameterSetLength
                pps_hdr.frame.putShort((short) pps.length);

                pps_hdr.frame.rewind();
                frames.add(pps_hdr);

                // pictureParameterSetNALUnit
                SrsFlvFrameBytes pps_bb = new SrsFlvFrameBytes();
                pps_bb.size = pps.length;
                pps_bb.frame = ByteBuffer.wrap(pps);
                frames.add(pps_bb);
            }
        }

        public SrsFlvFrameBytes mux_avc2flv(ArrayList<SrsFlvFrameBytes> frames, int frame_type, int avc_packet_type, int dts, int pts) {
            SrsFlvFrameBytes flv_tag = new SrsFlvFrameBytes();

            // for h264 in RTMP video payload, there is 5bytes header:
            //      1bytes, FrameType | CodecID
            //      1bytes, AVCPacketType
            //      3bytes, CompositionTime, the cts.
            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            flv_tag.size = 5;
            for (int i = 0; i < frames.size(); i++) {
                SrsFlvFrameBytes frame = frames.get(i);
                flv_tag.size += frame.size;
            }

            flv_tag.frame = ByteBuffer.allocate(flv_tag.size);

            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            // Frame Type, Type of video frame.
            // CodecID, Codec Identifier.
            // set the rtmp header
            flv_tag.frame.put((byte)((frame_type << 4) | SrsCodecVideo.AVC));

            // AVCPacketType
            flv_tag.frame.put((byte)avc_packet_type);

            // CompositionTime
            // pts = dts + cts, or
            // cts = pts - dts.
            // where cts is the header in rtmp video packet payload header.
            int cts = pts - dts;
            flv_tag.frame.put((byte)(cts >> 16));
            flv_tag.frame.put((byte)(cts >> 8));
            flv_tag.frame.put((byte)cts);

            // h.264 raw data.
            for (int i = 0; i < frames.size(); i++) {
                SrsFlvFrameBytes frame = frames.get(i);
                byte[] frame_bytes = new byte[frame.size];
                frame.frame.get(frame_bytes);
                flv_tag.frame.put(frame_bytes);
            }

            // reset the buffer.
            flv_tag.frame.rewind();

            //Log.i(TAG, String.format("flv tag muxed, %dB", flv_tag.size));
            //FlvMuxer.srs_print_bytes(TAG, flv_tag.frame, 128);

            return flv_tag;
        }

        public SrsFlvFrameBytes annexb_demux(ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
            SrsFlvFrameBytes tbb = new SrsFlvFrameBytes();

            while (bb.position() < bi.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                // tbbsc stands for the result of searching nal unit header {isMatched, length}
                SrsAnnexbSearch tbbsc = utils.srs_avc_startswith_annexb(bb, bi);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    Log.e(TAG, "annexb not match.");
                    FlvMuxer.srs_print_bytes(TAG, bb, 16);
                    throw new Exception(String.format("annexb not match for %dB, pos=%d", bi.size, bb.position()));
                }

                // skip the start codes(nal unit header).
                ByteBuffer tbbs = bb.slice();
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                // find out the frame size.
                tbb.frame = bb.slice();
                int pos = bb.position();
                while (bb.position() < bi.size) {
                    SrsAnnexbSearch bsc = utils.srs_avc_startswith_annexb(bb, bi);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.size = bb.position() - pos;
                if (bb.position() < bi.size) {
                    Log.i(TAG, String.format("annexb multiple match ok, pts=%d", bi.presentationTimeUs / 1000));
                    FlvMuxer.srs_print_bytes(TAG, tbbs, 16);
                    FlvMuxer.srs_print_bytes(TAG, bb.slice(), 16);
                }
                //Log.i(TAG, String.format("annexb match %d bytes", tbb.size));
                break;
            }

            return tbb;
        }
    }

    /**
     * remux the annexb to flv tags.
     */
    class SrsFlv {
        private MediaFormat videoTrack;
        private MediaFormat audioTrack;
        private int achannel;
        private int asample_rate;

        private SrsUtils utils;
        private Handler handler;

        private SrsRawH264Stream avc;
        private byte[] h264_sps;
        private boolean h264_sps_changed;
        private byte[] h264_pps;
        private boolean h264_pps_changed;
        private boolean h264_sps_pps_sent;
        boolean hasMetSps = false;
        boolean hasMetPps = false;

        private byte[] aac_specific_config;
        private boolean aac_asc_sent;

        public SrsFlv() {
            utils = new SrsUtils();

            avc = new SrsRawH264Stream();
            h264_sps = new byte[0];
            h264_sps_changed = false;
            h264_pps = new byte[0];
            h264_pps_changed = false;
            h264_sps_pps_sent = false;

            aac_specific_config = null;
            aac_asc_sent = false;
        }

        /**
         * set the handler to send message to work thread.
         * @param h the handler to send the message.
         */
        public void setHandler(Handler h) {
            hasMetSps = false;
            hasMetPps = false;
            handler = h;
        }

        public void setVideoTrack(MediaFormat format) {
            videoTrack = format;
        }

        public void setAudioTrack(MediaFormat format) {
            audioTrack = format;
            achannel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            asample_rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        }

        private void put_be16(ByteBuffer data, short val) {
            byte[] buf = new byte[2];
            buf[1] = (byte)(val & 0xff);
            buf[0] = (byte)((val >> 8) & 0xff);
            data.put(buf);
        }

        private void put_be32(ByteBuffer data, int val)
        {
            byte[] buf = new byte[4];

            buf[3] = (byte)(val & 0xff);
            buf[2] = (byte)((val >> 8) & 0xff);
            buf[1] = (byte)((val >> 16) & 0xff);
            buf[0] = (byte)((val >> 24) & 0xff);

            data.put(buf);
        }

        private void put_name(ByteBuffer data, String name) {
            put_be16(data, (short)name.length());
            data.put(name.getBytes());
        }

        private void put_double(ByteBuffer data, double val) {
            data.put(AMFDataType.AMFNumber);

            long vall = Double.doubleToLongBits(val);
            byte[] buf = new byte[8];
            buf[7] = (byte)(vall & 0xff);
            buf[6] = (byte)((vall >> 8) & 0xff);
            buf[5] = (byte)((vall >> 16) & 0xff);
            buf[4] = (byte)((vall >> 24) & 0xff);
            buf[3] = (byte)((vall >> 32) & 0xff);
            buf[2] = (byte)((vall >> 40) & 0xff);
            buf[1] = (byte)((vall >> 48) & 0xff);
            buf[0] = (byte)((vall >> 56) & 0xff);

            data.put(buf);
        }

        private void put_string(ByteBuffer data, String string) {
            if (string.length() < 0xFFFF) {
                data.put(AMFDataType.AMFString);
                put_be16(data, (short)(string.length()&0xffff));
            } else {
                data.put(AMFDataType.AMFLongString);
                put_be32(data, string.length());
            }
            data.put(string.getBytes());
        }

        private void put_named_string(ByteBuffer data, String name, String val) {
            put_name(data, name);
            put_string(data, val);
        }

        private void put_named_double(ByteBuffer data, String name, double val) {
            put_name(data, name);
            put_double(data, val);
        }

        public SrsFlvFrameBytes makeMetaData(double width, double height, double fps, double videobitrate,
                                             double audiosamplerate, double audiodatarate) {
            ByteBuffer metainfo = ByteBuffer.allocate(1024);

            put_string(metainfo, "@setDataFrame");
            put_string(metainfo, "onMetaData");
            metainfo.put(AMFDataType.AMFObject);
            put_named_string(metainfo, "author", "Andy Young");
            put_named_string(metainfo, "copyright", "@Baidu.com");
            put_named_double(metainfo, "width", width);
            put_named_double(metainfo, "height", height);
            put_named_double(metainfo, "framerate", fps);
            put_named_double(metainfo, "videodatarate", videobitrate);
            put_named_double(metainfo, "audiosamplerate", audiosamplerate);
            put_named_double(metainfo, "audiodatarate", audiodatarate);
            put_name(metainfo, "");
            metainfo.put(AMFDataType.AMFObjectEnd);
            int position = metainfo.position();
            metainfo.rewind();

            SrsFlvFrameBytes metadata = new SrsFlvFrameBytes();
            metadata.frame = metainfo;
            metadata.size = position;
            
            return metadata;
        }

        public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
            int pts = (int)(bi.presentationTimeUs / 1000);
            int dts = (int) pts - CTS_OF_FRAME_IN_MS;
            dts = dts < 0 ? 0 : dts;

            byte[] frame = null;
            byte aac_packet_type = 1; // 1 = AAC raw
            if (aac_specific_config == null) {
                Log.d(TAG, "Generating aac audio specific config frame.");
                frame = new byte[4];
                aac_specific_config = new byte[2];
                aac_specific_config[0] = 0x12;
                aac_specific_config[1] = 0x10;
            } else {
                frame = new byte[bi.size + 2];
                bb.get(frame, 2, frame.length - 2);
            }

            byte sound_format = 10; // AAC
            byte sound_type = 0; // 0 = Mono sound
            if (achannel == 2) {
                sound_type = 1; // 1 = Stereo sound
            }
            byte sound_size = 2; // 2 = 16-bit samples
            byte sound_rate = 3; // 44100, 22050, 11025
            if (asample_rate == 22050) {
                sound_rate = 2;
            } else if (asample_rate == 11025) {
                sound_rate = 1;
            }

            // for audio frame, there is 1 or 2 bytes header:
            //  1bytes, SoundFormat|SoundRate|SoundSize|SoundType
            //  1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
            byte audio_header = (byte)(sound_type & 0x01);
            audio_header |= (sound_size << 1) & 0x02;
            audio_header |= (sound_rate << 2) & 0x0c;
            audio_header |= (sound_format << 4) & 0xf0;

            if (!aac_asc_sent) {
                aac_packet_type = 0;
                aac_asc_sent = true;
                System.arraycopy(aac_specific_config, 0, frame, 2, aac_specific_config.length);
            }
            
            frame[0] = audio_header;
            frame[1] = aac_packet_type;

            SrsFlvFrameBytes tag = new SrsFlvFrameBytes();
            tag.frame = ByteBuffer.wrap(frame);
            tag.size = frame.length;

            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Audio, timestamp, 0, aac_packet_type, tag);
        }

        public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
            int pts = (int)(bi.presentationTimeUs / 1000);
            int dts = pts - CTS_OF_FRAME_IN_MS;
            dts = dts < 0 ? 0 : dts;

            ArrayList<SrsFlvFrameBytes> ibps = new ArrayList<SrsFlvFrameBytes>();
            int frame_type = SrsCodecVideoAVCFrame.InterFrame;
            //Log.i(TAG, String.format("video %d/%d bytes, offset=%d, position=%d, pts=%d", bb.remaining(), bi.size, bi.offset, bb.position(), pts));

            // send each frame.
            while (bb.position() < bi.size) {
                SrsFlvFrameBytes frame = avc.annexb_demux(bb, bi);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);
                if (nal_unit_type == SrsAvcNaluType.SPS || nal_unit_type == SrsAvcNaluType.PPS) {
                    Log.i(TAG, String.format("annexb demux %dB, pts=%d, frame=%dB, nalu=%d", bi.size, pts, frame.size, nal_unit_type));
                }

                // for IDR frame, the frame is keyframe.
                if (nal_unit_type == SrsAvcNaluType.IDR) {
                    frame_type = SrsCodecVideoAVCFrame.KeyFrame;
                }

                // ignore the nalu type aud(9)
                if (nal_unit_type == SrsAvcNaluType.AccessUnitDelimiter) {
                    continue;
                }

                // for sps
                if (avc.is_sps(frame)) {
                    byte[] sps = new byte[frame.size];
                    frame.frame.get(sps);

                    if (utils.srs_bytes_equals(h264_sps, sps)) {
                        continue;
                    }
                    h264_sps_changed = true;
                    h264_sps = sps;
                    hasMetSps = true;
                    continue;
                }

                // for pps
                if (avc.is_pps(frame)) {
                    byte[] pps = new byte[frame.size];
                    frame.frame.get(pps);

                    if (utils.srs_bytes_equals(h264_pps, pps)) {
                        continue;
                    }
                    h264_pps_changed = true;
                    h264_pps = pps;
                    hasMetPps = true;
                    continue;
                }

                // ibp frame.
                SrsFlvFrameBytes nalu_header = avc.mux_ibp_frame(frame);
                ibps.add(nalu_header);
                ibps.add(frame);
            }

            if (!hasMetSps || !hasMetPps) {
                Log.d(TAG, "No sps and pps was sent, skip");
                return;
            }
            
            if (frame_type == SrsCodecVideoAVCFrame.KeyFrame) {
                write_h264_sps_pps(dts, pts);
            }

            write_h264_ipb_frame(ibps, frame_type, dts, pts);
        }

        private void write_h264_sps_pps(int dts, int pts) {
            // when sps or pps changed, update the sequence header,
            // for the pps maybe not changed while sps changed.
            // so, we must check when each video ts message frame parsed.
            if (h264_sps_pps_sent && !h264_sps_changed && !h264_pps_changed) {
                return;
            }

            // when not got sps/pps, wait.
            if (h264_pps.length <= 0 || h264_sps.length <= 0) {
                return;
            }

            // h264 raw to h264 packet.
            ArrayList<SrsFlvFrameBytes> frames = new ArrayList<SrsFlvFrameBytes>();
            avc.mux_sequence_header(h264_sps, h264_pps, dts, pts, frames);

            // h264 packet to flv packet.
            int frame_type = SrsCodecVideoAVCFrame.KeyFrame;
            int avc_packet_type = SrsCodecVideoAVCType.SequenceHeader;
            SrsFlvFrameBytes flv_tag = avc.mux_avc2flv(frames, frame_type, avc_packet_type, dts, pts);

            // the timestamp in rtmp message header is dts.
            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Video, timestamp, frame_type, avc_packet_type, flv_tag);

            // reset sps and pps.
            h264_sps_changed = false;
            h264_pps_changed = false;
            h264_sps_pps_sent = true;
            Log.i(TAG, String.format("flv: h264 sps/pps sent, sps=%dB, pps=%dB", h264_sps.length, h264_pps.length));
        }

        private void write_h264_ipb_frame(ArrayList<SrsFlvFrameBytes> ibps, int frame_type, int dts, int pts) {
            // when sps or pps not sent, ignore the packet.
            // @see https://github.com/simple-rtmp-server/srs/issues/203
            if (!h264_sps_pps_sent) {
                return;
            }

            int avc_packet_type = SrsCodecVideoAVCType.NALU;
            SrsFlvFrameBytes flv_tag = avc.mux_avc2flv(ibps, frame_type, avc_packet_type, dts, pts);

//            if (frame_type == SrsCodecVideoAVCFrame.KeyFrame) {
//                Log.i(TAG, String.format("flv: keyframe %dB, dts=%d", flv_tag.size, dts));
//            }

            // the timestamp in rtmp message header is dts.
            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Video, timestamp, frame_type, avc_packet_type, flv_tag);
        }

        private void rtmp_write_packet(int type, int dts, int frame_type, int avc_aac_type, SrsFlvFrameBytes tag) {
            SrsFlvFrame frame = new SrsFlvFrame();
            frame.tag = tag;
            frame.type = type;
            frame.dts = dts;
            frame.frame_type = frame_type;
            frame.avc_aac_type = avc_aac_type;

            // use handler to send the message.
            // TODO: FIXME: we must wait for the handler to ready, for the sps/pps cannot be dropped.
            if (handler == null) {
                Log.w(TAG, "flv: drop frame for handler not ready.");
                return;
            }

            Message msg = Message.obtain();
            msg.what = SrsMessageType.FLV;
            msg.obj = frame;
            handler.sendMessage(msg);
        }
    }
}
