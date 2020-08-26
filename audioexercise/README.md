# AudioExercise
## 1 获取权限
音频录制权限是必须要获取的；为了方便起见，没有使用外部目录，故未申请外部存储权限

    <!--音频录制权限 -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <!--读取和写入存储权限-->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    
## 2 AudioRecord录制
在Android平台上音频录制有两种api可供选择：MediaRecord或AudioRecord

MediaRecord：封装程度较高，因此简单几行代码就可以实现音频的录制、压缩、编码等功能，这样的便捷牺牲了灵活性

AudioRecord：提供了丰富的配置选项，可以暂停、操作采样的每一帧音频数据，管理音频文件的压缩、存储

以下为搬运的：
1. 优点：

* 可以录制音频、视频
* 提供了录制、压缩、编码等功能
* 使用简单方便，几行代码就可实现

2. 缺点：

* 可以录制的视频格式较少
* 录制的过程中不能暂停
* 不能实时处理音频数据（实时对讲的话用它就不适合了）
### 2.1 设置录制格式
* 采样率：每秒钟采样的次数

* 声道：声道数

* 量化精度：每次采样存储需要的位数

* 采样buffer大小：最小为：采样率\*声道\*量化精度 / 8

    可以看到仅返回最小的缓冲大小，不保证高loading下能够平滑录制

        /**
         * Returns the minimum buffer size required for the successful creation of an AudioRecord
         * object, in byte units.
         * Note that this size doesn't guarantee a smooth recording under load, and higher values
         * should be chosen according to the expected frequency at which the AudioRecord instance
         * will be polled for new data.
         * See {@link #AudioRecord(int, int, int, int, int)} for more information on valid
         * configuration values.
         * /
         static public int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat)

---
### 2.2 录制
因为录制过程需要等待IO、读录制缓冲、将音频数据实时写入保存等IO操作，最好使用单独的线程去录制

    // 创建AudioRecord对象
    mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    8000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    // 开始录制
    mAudioRecord.startRecording();
    byte[] buf = new byte[1024];
    while(isRecording) {
        // 需要等待IO
        int size = mAudioRecord.read(buf, 0, buf.length);
        if(size > 0) {
            fos.write(buf, 0, size);
        }
    }
    mAudioRecord.stop();
    fos.flush();
    fos.close();
    
    /*                  AudioRecord构造方法签名参考                   */
    /**
      * @param audioSource 使用的音频设备
      * @param sampleRateInHz 采样频率
      * @param channelConfig 声道配置，输入时一般选择CHANNEL_IN_MONO或CHANNEL_IN_STEREO
      * @param audioFormat 采样精度
      * @param bufferSizeInBytes 缓冲区字节数
    public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat,
            int bufferSizeInBytes)
---
### 2.3 PCM转WAV
使用AudioRecord录制保存的是原始的PCM采样数据，播放器播放时还需要一些**头信息**。这里我们使用wav格式进行保存，因此填充wav头。
#### 2.3.1 WAV格式简介
wav头格式如下表，占用44个字节，其后填充PCM采样数据

`注意`：**字符**统一使用大端字节序(big-endian)，**数字值**统一使用小端字节序(little-endian)

|偏移地址|命名|内容|
|---|---|---|
|00-03|ChunkId|"RIFF"
|04-07|ChunkSize|下个地址开始到文件尾的总字节数|
|08-11|fccType|"WAVE"|
|12-15|SubChunkId1|"fmt"，最后一位空格|
|16-19|SubChunkSize1|一般为16，表示fmt Chunk的数据块大小为16字节，即20-35|
|20-21|FormatTag|1:表示是PCM编码|
|22-23|Channels|声道数，单声道为1，双声道为2|
|24-27|SamplesPerSec|采样率|
|28-31|BytesPerSec|码率：采样率\*采样精度\*声道个数，bytePerSecond = sampleRate * (bitsPerSample / 8) * channels|
|32-33|BlockAlign|每次采样的大小：位宽*声道数/8
|34-35|BitsPerSample|位宽|
|36-39|SubChunkId2|"data"|
|40-43|SubChunkSize2|音频数据的长度|
|44-...|data|音频数据|

***
相关知识补充：FormatTag取值确定文件格式，我们使用wav格式，**1(0x0001)**(即PCM格式)，因此fmt长度(即**SubChunkSize1**)固定为16字节，于是头的长度固定为44字节

FormatTag格式代码取值如下表

| 格式代码           | 格式名称            | fmt块长度 | fact块 |
|:---------------|:----------------|:-------|:------|
| 1(0x0001)      | PCM/非压缩格式       | 16     |       |
| 2(0x0002       | Microsoft ADPCM | 18     | √     |
| 3(0x0003)      | IEEE float      | 18     | √     |
| 6(0x0006)      | ITU G.711 a-law | 18     | √     |
| 7(0x0007)      | ITU G.711 μ-law | 18     | √     |
| 49(0x0031)     | GSM 6.10        | 20     | √     |
| 64(0x0040)     | ITU G.721 ADPCM |          | √     |
| 65,534(0xFFFE) | 见子格式块中的编码格式     | 40     |        |

PCM格式，可以无损无压缩保存采集到的所有数据，但相对的，使用的空间会比较大

---
## 3 AudioTrack播放
类似录制的api，同样有几种播放的方式可以选择：MediaPlayer、SoundPool、AudioTrack

MediaPlayer：封装层次较高的API，自动解析头

SoundPool：支持多个音频文件同时播放

AudioTrack：可以设置播放格式，手动写入音频帧数据，可控性高

同样，在MODE_STREAM模式下，播放过程需要不断向AudioTrack的缓冲区写入帧数据，最好使用单独的线程去播放

    // 读取文件头
    byte[] header = new byte[44];
    PcmWavUtil.WavHeader wavHeader = PcmWavUtil.transformToWavHeader(header);
    // 配置AudioTrack参数
    AudioTrack audioTrack = new AudioTrack(
        new AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        new AudioFormat.Builder()
                .setEncoding(bits2AudioFormat(wavHeader.bitsPerSample))
                .setChannelMask(wavHeader.channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO)
                .setSampleRate(wavHeader.samplesPerSec)
                .build(),
        wavHeader.bytesPerSec,
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE);
    // 开始播放
    audioTrack.play();
    int ret;
    byte[] buf = new byte[wavHeader.bytesPerSec];
    while((ret = fis.read(buf, 0, buf.length)) != -1){
        // 写入帧数据，会阻塞直到已经写入的数据全部被消耗掉
        audioTrack.write(buf, 0,ret);
    }
    audioTrack.stop();
    /*                      AudioTrack构造方法签名参考                  */
    /** 
      * @param attributes, 参考AudioAttributes，包括streamType、contentType，
      *                    与Android定义的音频输出配置有关，与文件头无关
      * @param format，参考AudioFormat，根据文件头内容进行配置，
      *                包括encoding、channelMask、sampleRate，分别对应
      *                编码格式(对于pcm来讲，即采样精度)、声道掩码、采样率
      * @param bufferSizeInBytes，如果使用流式缓冲，一般传入音频帧字节数的整倍数
      *                           如果是静态缓冲，传入音频数据的长度
      * @param mode, MODE_STATIC、MODE_STREAM，分别指，静态缓冲、流式缓冲
      * @param sessionId，为该AudioTrack指定一个sessionId，可以复用sessionId，
      *                   也可以传入AUDIO_SESSION_ID_GENERATE自动生成
      * /
    public AudioTrack(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes,
            int mode, int sessionId)

