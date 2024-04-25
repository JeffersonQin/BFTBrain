from typing import List

class LogItemBase:
    def __init__(self, timestamp: str, unit_id: int, sequence_number: str):
        self.timestamp = timestamp
        self.unit_id = unit_id
        self.sequence_number = sequence_number


class ProcessLogItem(LogItemBase):
    def __init__(self, timestamp: str, unit_id: int, sequence_number: str, message_type: str, from_: int, to: List[int]):
        super().__init__(timestamp, unit_id, sequence_number)
        self.message_type = message_type
        self.from_ = from_
        self.to = to


class TransitionLogItem(LogItemBase):
    def __init__(self, timestamp: str, unit_id: int, sequence_number: str, from_state: str, to_state: str):
        super().__init__(timestamp, unit_id, sequence_number)
        self.from_state = from_state
        self.to_state = to_state


class SendLogItem(LogItemBase):
    def __init__(self, timestamp: str, unit_id: int, sequence_number: str, message_type: str, to: List[int]):
        super().__init__(timestamp, unit_id, sequence_number)
        self.message_type = message_type
        self.to = to


class TallyLogItem(LogItemBase):
    def __init__(self, timestamp: str, unit_id: int, sequence_number: str, message_type: str, from_: int, to: List[int]):
        super().__init__(timestamp, unit_id, sequence_number)
        self.message_type = message_type
        self.from_ = from_
        self.to = to

class StateUpdateLoopLogItem(LogItemBase):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)


class ExecutionItem(LogItemBase):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
