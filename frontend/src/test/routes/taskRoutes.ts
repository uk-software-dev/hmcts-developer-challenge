import { app } from '../../main/app';

import axios from 'axios';
import request from 'supertest';

jest.mock('axios', () => ({
  __esModule: true,
  default: {
    delete: jest.fn(),
    get: jest.fn(),
    patch: jest.fn(),
    post: jest.fn(),
  },
}));

const task = {
  id: 12,
  caseId: 1,
  title: 'Prepare hearing bundle',
  description: 'Collect the documents for the hearing',
  statusCode: '0002',
  status: 'In progress',
  dueDate: '2027-01-15T00:00:00',
  dueDateDay: 15,
  dueDateMonth: 1,
  dueDateYear: 2027,
};

const caseData = {
  id: 1,
  caseNumber: 'CASE-123',
  title: 'Example case',
  description: 'Case details for testing',
  status: 'Open',
  localDate: '01/11/2026',
};

function axiosResponse<T>(data: T): Awaited<ReturnType<typeof axios.get<T>>> {
  return { data } as unknown as Awaited<ReturnType<typeof axios.get<T>>>;
}

describe('Task and case routes', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  test('GET / renders the case returned by the backend', async () => {
    jest.mocked(axios.get).mockResolvedValueOnce(axiosResponse(caseData));

    await request(app)
      .get('/')
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('CASE-123');
        expect(response.text).toContain('Example case');
      });

    expect(axios.get).toHaveBeenCalledWith('http://localhost:4000/get-example-case');
  });

  test('GET /tasks/list requests the case tasks and renders the task list', async () => {
    jest.mocked(axios.get).mockResolvedValueOnce(axiosResponse([task]));

    await request(app)
      .get('/tasks/list')
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Prepare hearing bundle');
        expect(response.text).toContain('/task/12/open');
      });

    expect(axios.get).toHaveBeenCalledWith('http://localhost:4000/get-task-list', {
      params: { caseId: 1 },
    });
  });

  test('GET /tasks/list returns 502 when the backend request fails', async () => {
    jest.mocked(axios.get).mockRejectedValueOnce(new Error('backend unavailable'));

    await request(app).get('/tasks/list').expect(502, 'Unable to load tasks');
    expect(axios.get).toHaveBeenCalledWith('http://localhost:4000/get-task-list', {
      params: { caseId: 1 },
    });
  });

  test('GET /get-tasks returns task data as JSON', async () => {
    jest.mocked(axios.get).mockResolvedValueOnce(axiosResponse([task]));

    await request(app).get('/get-tasks').expect(200).expect('Content-Type', /json/).expect([task]);

    expect(axios.get).toHaveBeenCalledWith('http://localhost:4000/get-task-list');
  });

  test('GET /task/:id/open renders the requested task', async () => {
    jest.mocked(axios.get).mockResolvedValueOnce(axiosResponse(task));

    await request(app)
      .get('/task/12/open')
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Prepare hearing bundle');
        expect(response.text).toContain('Collect the documents for the hearing');
      });

    expect(axios.get).toHaveBeenCalledWith('http://localhost:4000/get-task-by-id', {
      params: { caseId: 1, taskId: '12' },
    });
  });

  test('POST /tasks/create renders a blank task form', async () => {
    await request(app)
      .post('/tasks/create')
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Create New Task');
        expect(response.text).toContain('Title');
        expect(response.text).toContain("action='/task/save'");
      });

    expect(axios.get).not.toHaveBeenCalled();
  });

  test('POST /task/save renders validation errors without calling the backend', async () => {
    await request(app)
      .post('/task/save')
      .type('form')
      .send({
        txtTitle: ' ',
        txtTaskDescription: '',
        selectStatus: '',
        'dateDueDate-day': '31',
        'dateDueDate-month': '2',
        'dateDueDate-year': '2027',
      })
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Enter a title for this task');
        expect(response.text).toContain('Enter a description for this task');
        expect(response.text).toContain('Select a status for this task');
        expect(response.text).toContain('Enter a valid calendar date');
        expect(response.text).toContain("action='/task/save'");
      });

    expect(axios.post).not.toHaveBeenCalled();
  });

  test('correcting an invalid due date allows the task to be created', async () => {
    const taskForm = {
      txtTitle: 'Prepare hearing bundle',
      txtTaskDescription: 'Collect the documents for the hearing',
      selectStatus: '0002',
      'dateDueDate-day': '31',
      'dateDueDate-month': '2',
      'dateDueDate-year': '2027',
    };

    await request(app)
      .post('/task/save')
      .type('form')
      .send(taskForm)
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Enter a valid calendar date');
        expect(response.text).toContain("action='/task/save'");
      });

    jest.mocked(axios.post).mockResolvedValueOnce(axiosResponse({ ...task, id: 35 }));

    await request(app)
      .post('/task/save')
      .type('form')
      .send({
        ...taskForm,
        'dateDueDate-day': '15',
        'dateDueDate-month': '1',
      })
      .expect(302)
      .expect('Location', '/task/35/open');

    expect(axios.post).toHaveBeenCalledWith(
      'http://localhost:4000/save-task',
      expect.objectContaining({ dueDate: '2027-01-15T00:00:00' })
    );
  });

  test('POST /task/save creates a valid task and redirects to it', async () => {
    jest.mocked(axios.post).mockResolvedValueOnce(axiosResponse({ ...task, id: 34 }));

    await request(app)
      .post('/task/save')
      .type('form')
      .send({
        txtTitle: 'Prepare hearing bundle',
        txtTaskDescription: 'Collect the documents for the hearing',
        selectStatus: '0002',
        'dateDueDate-day': '15',
        'dateDueDate-month': '1',
        'dateDueDate-year': '2027',
      })
      .expect(302)
      .expect('Location', '/task/34/open');

    expect(axios.post).toHaveBeenCalledWith('http://localhost:4000/save-task', {
      id: 0,
      caseId: 1,
      title: 'Prepare hearing bundle',
      description: 'Collect the documents for the hearing',
      statusCode: '0002',
      status: '',
      dueDate: '2027-01-15T00:00:00',
      dueDateDay: 15,
      dueDateMonth: 1,
      dueDateYear: 2027,
    });
  });

  test('POST /task/save/:id updates a changed task status', async () => {
    jest.mocked(axios.get).mockResolvedValueOnce(axiosResponse(task));
    jest.mocked(axios.patch).mockResolvedValueOnce(axiosResponse({ ...task, statusCode: '0004', status: 'Complete' }));

    await request(app)
      .post('/task/save/12')
      .type('form')
      .send({ selectStatus: '0004' })
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Complete');
      });

    expect(axios.patch).toHaveBeenCalledWith('http://localhost:4000/patch-task/12/0004');
  });

  test('GET /task/:id/delete deletes the task and shows a confirmation', async () => {
    jest.mocked(axios.delete).mockResolvedValueOnce(axiosResponse(undefined));

    await request(app)
      .get('/task/12/delete')
      .expect(200)
      .expect(response => {
        expect(response.text).toContain('Task 12 deleted successfully');
      });

    expect(axios.delete).toHaveBeenCalledWith('http://localhost:4000/delete-task/12');
  });
});
