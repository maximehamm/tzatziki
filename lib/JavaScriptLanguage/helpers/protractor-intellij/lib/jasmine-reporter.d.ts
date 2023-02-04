declare namespace jasmine {
    interface Result {
        id: string;
        description: string;
        status: string;
        failedExpectations: FailedExpectation[];
    }

    interface FailedExpectation {
        actual: any;
        expected: any;
        message: string;
        stack: string;
    }
}
