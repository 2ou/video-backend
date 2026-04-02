DEFAULT_CANVAS_JSON = {
    "viewport": {"x": 0, "y": 0, "zoom": 1},
    "nodes": [
        {
            "id": "node_video_1",
            "type": "input_video",
            "position": {"x": 100, "y": 100},
            "data": {"label": "输入视频", "asset_id": None},
        },
        {
            "id": "node_prompt_1",
            "type": "prompt_input",
            "position": {"x": 100, "y": 260},
            "data": {"label": "提示词", "text": "make the video more cinematic"},
        },
        {
            "id": "node_kie_1",
            "type": "kie_video_task",
            "position": {"x": 420, "y": 180},
            "data": {
                "label": "KIE视频处理",
                "params": {"model": "video-model-a", "duration": 5, "resolution": "1080p"},
            },
        },
        {
            "id": "node_output_1",
            "type": "output_video",
            "position": {"x": 760, "y": 180},
            "data": {"label": "输出视频"},
        },
    ],
    "edges": [
        {"id": "e1", "source": "node_video_1", "target": "node_kie_1"},
        {"id": "e2", "source": "node_prompt_1", "target": "node_kie_1"},
        {"id": "e3", "source": "node_kie_1", "target": "node_output_1"},
    ],
}
